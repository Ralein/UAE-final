import { Component, signal, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MockFaceService, FaceVerification, UsernameType } from '../../core/services/mock-face.service';

@Component({
    selector: 'app-face-verify',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './face-verify.html',
    styleUrl: './face-verify.css',
})
export class FaceVerifyComponent {
    view = signal<'form' | 'scanning' | 'result'>('form');
    purpose = 'Document Signing Authorization';
    usernameType: UsernameType = 'EID';
    history: FaceVerification[] = [];

    constructor(public faceService: MockFaceService, private router: Router, private cdr: ChangeDetectorRef) {
        this.history = faceService.getVerificationHistory();
    }

    async startVerification() {
        this.view.set('scanning');
        this.cdr.markForCheck();
        await this.faceService.initiateFaceVerify(this.purpose, this.usernameType);
        this.history = this.faceService.getVerificationHistory();
        this.view.set('result');
        this.cdr.markForCheck();
    }

    reset() {
        this.faceService.clearActive();
        this.view.set('form');
        this.cdr.markForCheck();
    }

    getExpiryCountdown(): string {
        const v = this.faceService.currentVerification();
        if (!v?.expiresAt) return '';
        const diff = new Date(v.expiresAt).getTime() - Date.now();
        if (diff <= 0) return 'Expired';
        const mins = Math.floor(diff / 60000);
        const secs = Math.floor((diff % 60000) / 1000);
        return `${mins}m ${secs}s remaining`;
    }

    goBack() { this.router.navigate(['/dashboard']); }
}
