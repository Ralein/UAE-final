import { Component, signal, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MockHashSignService, HashSignJob, BulkHashJob } from '../../core/services/mock-hashsign.service';

@Component({
    selector: 'app-hashsign',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './hashsign.html',
    styleUrl: './hashsign.css',
})
export class HashsignComponent {
    mode = signal<'single' | 'bulk'>('single');
    view = signal<'upload' | 'processing' | 'complete'>('upload');
    selectedFile: File | null = null;
    fileName = '';
    bulkFiles: File[] = [];
    history: HashSignJob[] = [];

    constructor(public hashService: MockHashSignService, private router: Router, private cdr: ChangeDetectorRef) {
        this.history = hashService.getHistory();
    }

    setMode(m: 'single' | 'bulk') { this.mode.set(m); this.reset(); this.cdr.markForCheck(); }

    onFileSelect(e: Event) {
        const input = e.target as HTMLInputElement;
        if (!input.files) return;
        if (this.mode() === 'single') {
            this.selectedFile = input.files[0]; this.fileName = input.files[0].name;
        } else {
            this.bulkFiles = Array.from(input.files);
        }
        this.cdr.markForCheck();
    }

    async startSingleSign() {
        if (!this.selectedFile) return;
        this.view.set('processing');
        this.cdr.markForCheck();
        await this.hashService.initiateHashSign(this.fileName);
        this.history = this.hashService.getHistory();
        this.view.set('complete');
        this.cdr.markForCheck();
    }

    async startBulkSign() {
        if (this.bulkFiles.length === 0) return;
        this.view.set('processing');
        this.cdr.markForCheck();
        await this.hashService.initiateBulkSign(this.bulkFiles.map(f => f.name));
        this.view.set('complete');
        this.cdr.markForCheck();
    }

    downloadSigned() {
        const job = this.hashService.currentJob();
        if (!job) return;
        const blob = this.hashService.getSignedDocument(job.jobId);
        const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = `hashsigned_${job.fileName}`; a.click();
    }

    reset() {
        this.hashService.clearJobs(); this.selectedFile = null; this.fileName = '';
        this.bulkFiles = []; this.view.set('upload'); this.cdr.markForCheck();
    }

    getStepLabel(step: string): string {
        const map: Record<string, string> = {
            IDLE: 'Ready', SDK_PROCESSING: 'SDK Processing Hash…', AWAITING_USER: 'Awaiting Approval',
            SIGNING: 'Embedding Signature…', SIGNED: 'Signed ✓', FAILED: 'Failed',
        };
        return map[step] || step;
    }

    goBack() { this.router.navigate(['/dashboard']); }
}
