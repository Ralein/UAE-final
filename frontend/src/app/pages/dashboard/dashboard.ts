import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MockAuthService, MockUserProfile } from '../../core/services/mock-auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardComponent implements OnInit {
  userProfile: MockUserProfile | null = null;
  sessionInfo: { token: string; loginTime: string; expiresAt: string } | null = null;
  loading = true;

  constructor(
    private authService: MockAuthService,
    private router: Router
  ) { }

  ngOnInit() {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/']);
      return;
    }

    // Simulate loading delay
    setTimeout(() => {
      this.userProfile = this.authService.getProfile();
      this.sessionInfo = this.authService.getSessionInfo();
      this.loading = false;
    }, 600);
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/']);
  }

  get formattedLoginTime(): string {
    if (!this.sessionInfo) return '';
    return new Date(this.sessionInfo.loginTime).toLocaleString();
  }

  get formattedExpiry(): string {
    if (!this.sessionInfo) return '';
    return new Date(this.sessionInfo.expiresAt).toLocaleString();
  }
}
