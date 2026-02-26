package com.yoursp.uaepass.modules.linking;

import com.yoursp.uaepass.config.UaePassProperties;
import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.auth.CryptoUtil;
import com.yoursp.uaepass.modules.auth.StateService;
import com.yoursp.uaepass.modules.auth.dto.StatePayload;
import com.yoursp.uaepass.modules.auth.exception.InvalidStateException;
import com.yoursp.uaepass.modules.linking.dto.LinkConflictResponse;
import com.yoursp.uaepass.modules.linking.dto.LinkStatusResponse;
import com.yoursp.uaepass.modules.linking.exception.LinkConflictException;
import com.yoursp.uaepass.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for UAE PASS account linking / unlinking.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 * <li>GET /auth/link — Start manual linking flow (redirect to UAE PASS)</li>
 * <li>GET /auth/link/callback — Complete linking after UAE PASS redirect</li>
 * <li>DELETE /auth/unlink — Remove UAE PASS link</li>
 * <li>GET /auth/link/status — Check current link status</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserLinkingController {

    private final UaePassProperties uaePassProperties;
    private final StateService stateService;
    private final ManualLinkingService manualLinkingService;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    // ================================================================
    // GET /auth/link — Redirect to UAE PASS for manual linking
    // ================================================================

    @GetMapping("/link")
    public void startLink(HttpServletRequest request, HttpServletResponse response) throws Exception {
        User currentUser = getCurrentUser(request);
        if (currentUser == null) {
            response.sendRedirect(frontendUrl + "/auth/error?error=not_authenticated");
            return;
        }

        String state = stateService.generateState("MANUAL_LINK", "/settings", currentUser.getId());

        String authorizeUrl = UriComponentsBuilder.fromHttpUrl(uaePassProperties.getAuthorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", uaePassProperties.getClientId())
                .queryParam("redirect_uri", uaePassProperties.getRedirectUri())
                .queryParam("scope", uaePassProperties.getScope())
                .queryParam("state", state)
                .queryParam("acr_values", uaePassProperties.getAcrValues())
                .queryParam("ui_locales", uaePassProperties.getUiLocales())
                .build()
                .toUriString();

        log.info("Starting manual link for user {} (state={})", currentUser.getId(), state);
        response.sendRedirect(authorizeUrl);
    }

    // ================================================================
    // GET /auth/link/callback — Complete the linking
    // ================================================================

    @GetMapping("/link/callback")
    public void linkCallback(@RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletResponse response) throws Exception {

        // Handle UAE PASS error
        if (error != null) {
            log.warn("UAE PASS link error: {}", error);
            response.sendRedirect(frontendUrl + "/settings?link_error=" + error);
            return;
        }

        // Consume state — verify it's a MANUAL_LINK flow
        StatePayload statePayload;
        try {
            statePayload = stateService.consumeState(state);
        } catch (InvalidStateException ex) {
            log.warn("Invalid state during link callback: {}", ex.getMessage());
            response.sendRedirect(frontendUrl + "/settings?link_error=invalid_state");
            return;
        }

        if (!"MANUAL_LINK".equals(statePayload.flowType())) {
            log.warn("State flow type mismatch: expected MANUAL_LINK, got {}", statePayload.flowType());
            response.sendRedirect(frontendUrl + "/settings?link_error=invalid_flow");
            return;
        }

        UUID userId = statePayload.userId();
        if (userId == null) {
            log.error("No userId in MANUAL_LINK state");
            response.sendRedirect(frontendUrl + "/settings?link_error=missing_user");
            return;
        }

        // Exchange code for tokens
        Map<String, Object> tokenResponse = exchangeCodeForTokens(code);
        String accessToken = (String) tokenResponse.get("access_token");

        if (accessToken == null) {
            log.error("No access_token in link token response");
            response.sendRedirect(frontendUrl + "/settings?link_error=token_exchange_failed");
            return;
        }

        // Fetch user info from UAE PASS
        Map<String, Object> userInfo = fetchUserInfo(accessToken);
        String uaepassUuid = (String) userInfo.get("uuid");

        if (uaepassUuid == null || uaepassUuid.isBlank()) {
            log.error("No uuid in UAE PASS userinfo response");
            response.sendRedirect(frontendUrl + "/settings?link_error=missing_uuid");
            return;
        }

        // Attempt to link — ManualLinkingService handles conflict detection
        try {
            manualLinkingService.linkBySession(userId, uaepassUuid);
        } catch (LinkConflictException ex) {
            log.warn("Link conflict for user {}: {}", userId, ex.getErrorCode());
            response.sendRedirect(frontendUrl + "/settings?link_error=" + ex.getErrorCode());
            return;
        }

        log.info("Manual link complete: user {} → uaepass_uuid {}", userId, uaepassUuid);
        response.sendRedirect(frontendUrl + "/settings?linked=true");
    }

    // ================================================================
    // DELETE /auth/unlink — Remove UAE PASS link
    // ================================================================

    @DeleteMapping("/unlink")
    public ResponseEntity<Void> unlink(HttpServletRequest request) {
        User currentUser = getCurrentUser(request);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        manualLinkingService.unlinkUser(currentUser.getId());

        return ResponseEntity.noContent().build();
    }

    // ================================================================
    // GET /auth/link/status — Check current link status
    // ================================================================

    @GetMapping("/link/status")
    public ResponseEntity<LinkStatusResponse> linkStatus(HttpServletRequest request) {
        User currentUser = getCurrentUser(request);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Re-fetch from DB for latest state
        User user = userRepository.findById(currentUser.getId()).orElse(currentUser);

        boolean isLinked = user.getUaepassUuid() != null;

        LinkStatusResponse status = LinkStatusResponse.builder()
                .linked(isLinked)
                .linkedAt(user.getLinkedAt() != null ? user.getLinkedAt().toString() : null)
                .userType(user.getUserType())
                .build();

        return ResponseEntity.ok(status);
    }

    // ================================================================
    // Exception handler for LinkConflictException
    // ================================================================

    @ExceptionHandler(LinkConflictException.class)
    public ResponseEntity<LinkConflictResponse> handleLinkConflict(LinkConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new LinkConflictResponse(ex.getErrorCode(), ex.getMessage()));
    }

    // ================================================================
    // Private helpers (reuse same UAE PASS API calls as AuthController)
    // ================================================================

    private User getCurrentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCodeForTokens(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(uaePassProperties.getClientId(), uaePassProperties.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", uaePassProperties.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                uaePassProperties.getTokenUrl(),
                HttpMethod.POST,
                entity,
                Map.class);
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                uaePassProperties.getUserInfoUrl(),
                HttpMethod.GET,
                entity,
                Map.class);
        return resp.getBody();
    }
}
