import { Injectable, signal } from '@angular/core';

export type SealType = 'PADES' | 'CADES';
export type SealStatus = 'PROCESSING' | 'SEALED' | 'FAILED';

export interface ESealJob {
    jobId: string;
    fileName: string;
    sealType: SealType;
    status: SealStatus;
    signerName: string;
    signingTime: string;
    createdAt: string;
}

export interface ESealVerifyResult {
    valid: boolean;
    signerName: string;
    signingTime: string;
    certificateInfo: string;
}

const HISTORY_KEY = 'uaepass_eseal_history';

@Injectable({ providedIn: 'root' })
export class MockESealService {
    private activeJob = signal<ESealJob | null>(null);
    currentJob = this.activeJob.asReadonly();

    async sealPdf(fileName: string): Promise<ESealJob> {
        return this.seal(fileName, 'PADES');
    }

    async sealDocument(fileName: string): Promise<ESealJob> {
        return this.seal(fileName, 'CADES');
    }

    private async seal(fileName: string, type: SealType): Promise<ESealJob> {
        const job: ESealJob = {
            jobId: this.uuid(),
            fileName,
            sealType: type,
            status: 'PROCESSING',
            signerName: '',
            signingTime: '',
            createdAt: new Date().toISOString(),
        };
        this.activeJob.set(job);

        await this.delay(3000);

        const completed: ESealJob = {
            ...job,
            status: 'SEALED',
            signerName: 'CN=YourOrg eSeal, O=Your Organization, L=Dubai, C=AE',
            signingTime: new Date().toISOString(),
        };
        this.activeJob.set(completed);
        this.saveToHistory(completed);
        return completed;
    }

    async verifySeal(fileName: string): Promise<ESealVerifyResult> {
        await this.delay(2000);
        return {
            valid: true,
            signerName: 'CN=YourOrg eSeal, O=Your Organization, L=Dubai, C=AE',
            signingTime: new Date().toISOString(),
            certificateInfo: `Serial: ${Math.random().toString(16).slice(2, 18).toUpperCase()}, Issuer: UAE PASS CA, Valid: 2024-2027`,
        };
    }

    getSealedDocument(_jobId: string): Blob {
        return new Blob([`%PDF-1.4\n[Mock eSeal Document]\nOrganizationally sealed via UAE PASS.\n%%EOF`], { type: 'application/pdf' });
    }

    getSealHistory(): ESealJob[] {
        const stored = localStorage.getItem(HISTORY_KEY);
        if (!stored) return [];
        try { return JSON.parse(stored); } catch { return []; }
    }

    clearActiveJob(): void {
        this.activeJob.set(null);
    }

    private saveToHistory(job: ESealJob): void {
        const history = this.getSealHistory();
        history.unshift(job);
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
