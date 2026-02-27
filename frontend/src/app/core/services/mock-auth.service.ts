import { Injectable, signal, computed } from '@angular/core';

export interface MockUserProfile {
    firstname: string;
    lastnameEn: string;
    fullnameEn: string;
    email: string;
    mobile: string;
    nationalityEn: string;
    nationalityAr: string;
    idn: string;
    userType: string;
    uaepassUuid: string;
    gender: string;
    dob: string;
}

const MOCK_USERS: Record<string, MockUserProfile> = {
    default: {
        firstname: 'Ahmed',
        lastnameEn: 'Al Mansoori',
        fullnameEn: 'Ahmed Khalid Al Mansoori',
        email: 'ahmed.mansoori@email.ae',
        mobile: '971501234567',
        nationalityEn: 'United Arab Emirates',
        nationalityAr: 'الإمارات العربية المتحدة',
        idn: '784-1990-1234567-1',
        userType: 'SOP1',
        uaepassUuid: 'uaepass-a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        gender: 'Male',
        dob: '1990-03-15',
    },
};

const SESSION_KEY = 'uaepass_mock_session';

@Injectable({ providedIn: 'root' })
export class MockAuthService {
    private _isLoggedIn = signal(this.checkSession());

    isLoggedIn = this._isLoggedIn.asReadonly();

    private checkSession(): boolean {
        return !!localStorage.getItem(SESSION_KEY);
    }

    /**
     * Simulates the entire UAE PASS login flow.
     * Returns a Promise that resolves after the simulated delay.
     */
    async login(emiratesId: string): Promise<MockUserProfile> {
        // Simulate network delay for authentication
        await this.delay(1500);

        const profile = { ...MOCK_USERS['default'], idn: emiratesId };

        localStorage.setItem(SESSION_KEY, JSON.stringify({
            profile,
            token: this.generateMockToken(),
            loginTime: new Date().toISOString(),
            expiresAt: new Date(Date.now() + 3600000).toISOString(),
        }));

        this._isLoggedIn.set(true);
        return profile;
    }

    getProfile(): MockUserProfile | null {
        const session = localStorage.getItem(SESSION_KEY);
        if (!session) return null;
        try {
            return JSON.parse(session).profile;
        } catch {
            return null;
        }
    }

    getSessionInfo(): { token: string; loginTime: string; expiresAt: string } | null {
        const session = localStorage.getItem(SESSION_KEY);
        if (!session) return null;
        try {
            const parsed = JSON.parse(session);
            return { token: parsed.token, loginTime: parsed.loginTime, expiresAt: parsed.expiresAt };
        } catch {
            return null;
        }
    }

    logout(): void {
        localStorage.removeItem(SESSION_KEY);
        this._isLoggedIn.set(false);
    }

    /** Generates a mock 4-digit verification code */
    generateVerificationCode(): string {
        return Math.floor(1000 + Math.random() * 9000).toString();
    }

    private generateMockToken(): string {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
        let result = 'mock_jwt_';
        for (let i = 0; i < 32; i++) {
            result += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        return result;
    }

    private delay(ms: number): Promise<void> {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}
