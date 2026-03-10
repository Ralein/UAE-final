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
        const b64 = 'JVBERi0xLjQKJcOkw7zDtsOfCjEgMCBvYmoKPDwvVHlwZS9DYXRhbG9nL1BhZ2VzIDIgMCBSPj4KZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9LaWRzWzMgMCBSXS9Db3VudCAxPj4KZW5kb2JqCjMgMCBvYmoKPDwvVHlwZS9QYWdlL01lZGlhQm94WzAgMCA1OTUgODQyXS9QYXJlbnQgMiAwIFIvUmVzb3VyY2VzPDwvRm9udDw8L0YxIDQgMCBSPj4+Pi9Db250ZW50cyA1IDAgUj4+CmVuZG9iago0IDAgb2JqCjw8L1R5cGUvRm9udC9TdWJ0eXBlL1R5cGUxL0Jhc2VGb250L0hlbHZldGljYT4+CmVuZG9iago1IDAgb2JqCjw8L0xlbmd0aCAzOD4+c3RyZWFtCkJUCi9GMSAyNCBUZgoxMDAgNzAwIFRkCihNb2NrIFVBRSBQQVNTIERvY3VtZW50KSBUagpFVAplbmRzdHJlYW0KZW5kb2JqCnhyZWYKMCA2CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxNSAwMDAwMCBuIAowMDAwMDAwMDY4IDAwMDAwIG4gCjAwMDAwMDAxMjUgMDAwMDAgbiAKMDAwMDAwMDI1MyAwMDAwMCBuIAowMDAwMDAwMzQxIDAwMDAwIG4gCnRyYWlsZXIKPDwvU2l6ZSA2L1Jvb3QgMSAwIFI+PgpzdGFydHhyZWYKNDI4CiUlRU9GCg==';
        const binStr = atob(b64);
        const bytes = new Uint8Array(binStr.length);
        for (let i = 0; i < binStr.length; i++) {
            bytes[i] = binStr.charCodeAt(i);
        }
        return new Blob([bytes], { type: 'application/pdf' });
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
