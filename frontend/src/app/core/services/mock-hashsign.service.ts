import { Injectable, signal } from '@angular/core';

export type HashSignStep = 'IDLE' | 'SDK_PROCESSING' | 'AWAITING_USER' | 'SIGNING' | 'SIGNED' | 'FAILED';

export interface HashSignJob {
    jobId: string;
    fileName: string;
    step: HashSignStep;
    txId: string;
    digestHash: string;
    signIdentityId: string;
    ltvApplied: boolean;
    createdAt: string;
    completedAt: string | null;
}

export interface BulkHashDoc {
    fileName: string;
    step: HashSignStep;
    digestHash: string;
}

export interface BulkHashJob {
    jobId: string;
    documents: BulkHashDoc[];
    overallStep: HashSignStep;
    digestsSummary: string;
    createdAt: string;
    completedAt: string | null;
}

const HISTORY_KEY = 'uaepass_hashsign_history';

@Injectable({ providedIn: 'root' })
export class MockHashSignService {
    private activeJob = signal<HashSignJob | null>(null);
    private activeBulk = signal<BulkHashJob | null>(null);

    currentJob = this.activeJob.asReadonly();
    currentBulkJob = this.activeBulk.asReadonly();

    async initiateHashSign(fileName: string): Promise<HashSignJob> {
        const job: HashSignJob = {
            jobId: this.uuid(),
            fileName,
            step: 'SDK_PROCESSING',
            txId: this.uuid(),
            digestHash: '',
            signIdentityId: '',
            ltvApplied: false,
            createdAt: new Date().toISOString(),
            completedAt: null,
        };
        this.activeJob.set(job);

        // Step 1: SDK processing
        await this.delay(2000);
        job.digestHash = this.mockSha256();
        job.signIdentityId = `sign-id-${Math.random().toString(36).slice(2, 10)}`;
        this.activeJob.set({ ...job });

        // Step 2: Awaiting user
        await this.delay(500);
        this.activeJob.set({ ...job, step: 'AWAITING_USER' });
        await this.delay(3000);

        // Step 3: Signing
        this.activeJob.set({ ...job, step: 'SIGNING' });
        await this.delay(2000);

        // Step 4: Signed
        const signed: HashSignJob = { ...job, step: 'SIGNED', ltvApplied: true, completedAt: new Date().toISOString() };
        this.activeJob.set(signed);
        this.saveToHistory(signed);
        return signed;
    }

    async initiateBulkSign(fileNames: string[]): Promise<BulkHashJob> {
        const docs: BulkHashDoc[] = fileNames.map(f => ({ fileName: f, step: 'SDK_PROCESSING' as HashSignStep, digestHash: '' }));
        const bulk: BulkHashJob = {
            jobId: this.uuid(),
            documents: docs,
            overallStep: 'SDK_PROCESSING',
            digestsSummary: '',
            createdAt: new Date().toISOString(),
            completedAt: null,
        };
        this.activeBulk.set(bulk);

        // SDK for each doc
        await this.delay(2000);
        bulk.documents = docs.map(d => ({ ...d, digestHash: this.mockSha256() }));
        bulk.digestsSummary = this.mockSha256();
        this.activeBulk.set({ ...bulk });

        await this.delay(500);
        this.activeBulk.set({ ...bulk, overallStep: 'AWAITING_USER' });
        await this.delay(3000);

        this.activeBulk.set({ ...bulk, overallStep: 'SIGNING' });
        await this.delay(2500);

        const done: BulkHashJob = {
            ...bulk,
            overallStep: 'SIGNED',
            documents: bulk.documents.map(d => ({ ...d, step: 'SIGNED' as HashSignStep })),
            completedAt: new Date().toISOString(),
        };
        this.activeBulk.set(done);
        return done;
    }

    getSignedDocument(_jobId: string): Blob {
        return new Blob([`%PDF-1.4\n[Mock Hash-Signed Document]\nPKCS#7 signature embedded.\n%%EOF`], { type: 'application/pdf' });
    }

    getHistory(): HashSignJob[] {
        const stored = localStorage.getItem(HISTORY_KEY);
        if (!stored) return [];
        try { return JSON.parse(stored); } catch { return []; }
    }

    clearJobs(): void {
        this.activeJob.set(null);
        this.activeBulk.set(null);
    }

    private saveToHistory(job: HashSignJob): void {
        const history = this.getHistory();
        history.unshift(job);
        if (history.length > 20) history.length = 20;
        localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
    }

    private mockSha256(): string {
        const chars = '0123456789abcdef';
        let hash = '';
        for (let i = 0; i < 64; i++) hash += chars[Math.floor(Math.random() * 16)];
        return hash;
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
