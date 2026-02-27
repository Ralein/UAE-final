import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MockAuthService } from '../../core/services/mock-auth.service';

type LoginState = 'idle' | 'verifying' | 'approving' | 'success';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginComponent {
  emiratesId = '';
  state = signal<LoginState>('idle');
  verificationCode = signal('');
  errorMessage = signal('');
  showTooltip = false;

  constructor(
    private authService: MockAuthService,
    private router: Router
  ) {
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/dashboard']);
    }
  }

  async onSubmit() {
    this.errorMessage.set('');

    const cleaned = this.emiratesId.replace(/[-\s]/g, '');
    if (!cleaned || cleaned.length < 5) {
      this.errorMessage.set('Wrong identifier. Please check the examples and try again');
      return;
    }

    this.showTooltip = false;

    // Step 1: Verifying
    this.state.set('verifying');
    await this.delay(1200);

    // Step 2: Show approval code
    this.verificationCode.set(this.authService.generateVerificationCode());
    this.state.set('approving');

    // Step 3: Simulate phone approval
    try {
      await this.authService.login(this.emiratesId);
      this.state.set('success');
      await this.delay(800);
      this.router.navigate(['/dashboard']);
    } catch {
      this.state.set('idle');
      this.errorMessage.set('Authentication failed. Please try again.');
    }
  }

  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}
