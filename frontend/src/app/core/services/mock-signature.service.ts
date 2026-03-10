import { Injectable, signal } from '@angular/core';

export type SigningStatus = 'PENDING' | 'INITIATED' | 'AWAITING_USER' | 'CALLBACK_RECEIVED' | 'COMPLETING' | 'SIGNED' | 'FAILED' | 'EXPIRED';

export interface SigningJob {
    jobId: string;
    fileName: string;
    status: SigningStatus;
    ltvApplied: boolean;
    createdAt: string;
    completedAt: string | null;
    signatureField: string;
    pageNumber: number;
}

export interface SignParams {
    signatureFieldName: string;
    pageNumber: number;
    x: number;
    y: number;
    width: number;
    height: number;
    showSignatureImage: boolean;
}

const HISTORY_KEY = 'uaepass_signing_history';

@Injectable({ providedIn: 'root' })
export class MockSignatureService {
    private activeJob = signal<SigningJob | null>(null);
    currentJob = this.activeJob.asReadonly();

    async initiateSigning(fileName: string, params: SignParams): Promise<SigningJob> {
        const job: SigningJob = {
            jobId: this.uuid(),
            fileName,
            status: 'PENDING',
            ltvApplied: false,
            createdAt: new Date().toISOString(),
            completedAt: null,
            signatureField: params.signatureFieldName,
            pageNumber: params.pageNumber,
        };
        this.activeJob.set(job);

        // Run state machine in background
        this.runStateMachine(job);
        return job;
    }

    private async runStateMachine(job: SigningJob): Promise<void> {
        const transitions: { status: SigningStatus; delayMs: number }[] = [
            { status: 'INITIATED', delayMs: 800 },
            { status: 'AWAITING_USER', delayMs: 2500 },
            { status: 'CALLBACK_RECEIVED', delayMs: 2000 },
            { status: 'COMPLETING', delayMs: 1500 },
            { status: 'SIGNED', delayMs: 1200 },
        ];

        for (const t of transitions) {
            await this.delay(t.delayMs);
            job = { ...job, status: t.status };
            if (t.status === 'SIGNED') {
                job.ltvApplied = true;
                job.completedAt = new Date().toISOString();
            }
            this.activeJob.set(job);
        }

        this.saveToHistory(job);
    }

    getSignedDocument(_jobId: string): Blob {
        const b64 = 'JVBERi0xLjQKJcOkw7zDtsOfCjEgMCBvYmoKPDwvVHlwZS9DYXRhbG9nL1BhZ2VzIDIgMCBSPj4KZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9LaWRzWzMgMCBSXS9Db3VudCAxPj4KZW5kb2JqCjMgMCBvYmoKPDwvVHlwZS9QYWdlL01lZGlhQm94WzAgMCA1OTUgODQyXS9QYXJlbnQgMiAwIFIvUmVzb3VyY2VzPDwvRm9udDw8L0YxIDQgMCBSPj4+Pi9Db250ZW50cyA1IDAgUj4+CmVuZG9iago0IDAgb2JqCjw8L1R5cGUvRm9udC9TdWJ0eXBlL1R5cGUxL0Jhc2VGb250L0hlbHZldGljYT4+CmVuZG9iago1IDAgb2JqCjw8L0xlbmd0aCAzOD4+c3RyZWFtCkJUCi9GMSAyNCBUZgoxMDAgNzAwIFRkCihNb2NrIFVBRSBQQVNTIERvY3VtZW50KSBUagpFVAplbmRzdHJlYW0KZW5kb2JqCnhyZWYKMCA2CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxNSAwMDAwMCBuIAowMDAwMDAwMDY4IDAwMDAwIG4gCjAwMDAwMDAxMjUgMDAwMDAgbiAKMDAwMDAwMDI1MyAwMDAwMCBuIAowMDAwMDAwMzQxIDAwMDAwIG4gCnRyYWlsZXIKPDwvU2l6ZSA2L1Jvb3QgMSAwIFI+PgpzdGFydHhyZWYKNDI4CiUlRU9GCg==';
        const binStr = atob(b64);
        const bytes = new Uint8Array(binStr.length);
        for (let i = 0; i < binStr.length; i++) {
            bytes[i] = binStr.charCodeAt(i);
        }
        return new Blob([bytes], { type: 'application/pdf' });
    }

    getSigningHistory(): SigningJob[] {
        const stored = localStorage.getItem(HISTORY_KEY);
        if (!stored) return [];
        try { return JSON.parse(stored); } catch { return []; }
    }

    private saveToHistory(job: SigningJob): void {
        const history = this.getSigningHistory();
        history.unshift(job);
        if (history.length > 20) history.length = 20;
        localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
    }

    clearActiveJob(): void {
        this.activeJob.set(null);
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
