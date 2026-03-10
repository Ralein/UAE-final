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
        const content = `%PDF-1.4\n[Mock Signed Document — ${new Date().toISOString()}]\nDigitally signed via UAE PASS with LTV validation applied.\n%%EOF`;
        return new Blob([content], { type: 'application/pdf' });
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
