import { Injectable, signal } from '@angular/core';

export type FaceVerifyStep = 'IDLE' | 'SENDING_PUSH' | 'WAITING_SCAN' | 'VERIFYING' | 'VERIFIED' | 'FAILED';
export type UsernameType = 'EID' | 'MOBILE' | 'EMAIL';

export interface FaceVerification {
    verificationId: string;
    purpose: string;
    usernameType: UsernameType;
    step: FaceVerifyStep;
    uuidMatch: boolean;
    verifiedAt: string | null;
    expiresAt: string | null;
    createdAt: string;
}

const HISTORY_KEY = 'uaepass_face_history';
const VERIFICATION_WINDOW_MS = 15 * 60 * 1000; // 15 minutes

@Injectable({ providedIn: 'root' })
export class MockFaceService {
    private activeVerification = signal<FaceVerification | null>(null);
    currentVerification = this.activeVerification.asReadonly();

    async initiateFaceVerify(purpose: string, usernameType: UsernameType): Promise<FaceVerification> {
        const v: FaceVerification = {
            verificationId: this.uuid(),
            purpose,
            usernameType,
            step: 'SENDING_PUSH',
            uuidMatch: false,
            verifiedAt: null,
            expiresAt: null,
            createdAt: new Date().toISOString(),
        };
        this.activeVerification.set(v);

        // Step 1: Sending push notification
        await this.delay(1500);
        this.activeVerification.set({ ...v, step: 'WAITING_SCAN' });

        // Step 2: Waiting for face scan
        await this.delay(3000);
        this.activeVerification.set({ ...v, step: 'VERIFYING' });

        // Step 3: Verifying identity (UUID match check)
        await this.delay(2000);
        const now = new Date();
        const verified: FaceVerification = {
            ...v,
            step: 'VERIFIED',
            uuidMatch: true,
            verifiedAt: now.toISOString(),
            expiresAt: new Date(now.getTime() + VERIFICATION_WINDOW_MS).toISOString(),
        };
        this.activeVerification.set(verified);
        this.saveToHistory(verified);
        return verified;
    }

    isRecentlyVerified(): boolean {
        const history = this.getVerificationHistory();
        const recent = history.find(v =>
            v.step === 'VERIFIED' && v.uuidMatch && v.expiresAt && new Date(v.expiresAt) > new Date()
        );
        return !!recent;
    }

    getRecentVerification(): FaceVerification | null {
        const history = this.getVerificationHistory();
        return history.find(v =>
            v.step === 'VERIFIED' && v.uuidMatch && v.expiresAt && new Date(v.expiresAt) > new Date()
        ) ?? null;
    }

    getVerificationHistory(): FaceVerification[] {
        const stored = localStorage.getItem(HISTORY_KEY);
        if (!stored) return [];
        try { return JSON.parse(stored); } catch { return []; }
    }

    clearActive(): void {
        this.activeVerification.set(null);
    }

    private saveToHistory(v: FaceVerification): void {
        const history = this.getVerificationHistory();
        history.unshift(v);
        if (history.length > 20) history.length = 20;
        localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
    }

    private uuid(): string {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
            const r = Math.random() * 16 | 0;
            return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
        });
    }

    private delay(ms: number): Promise<void> {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}
