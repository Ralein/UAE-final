import { Component, signal, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MockSignatureService, SigningJob, SignParams } from '../../core/services/mock-signature.service';

type PageView = 'upload' | 'signing' | 'complete';

@Component({
    selector: 'app-signature',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './signature.html',
    styleUrl: './signature.css',
})
export class SignatureComponent {
    view = signal<PageView>('upload');
    selectedFile: File | null = null;
    fileName = '';
    params: SignParams = { signatureFieldName: 'Signature1', pageNumber: 1, x: 100, y: 100, width: 200, height: 60, showSignatureImage: true };
    history: SigningJob[] = [];
    dragOver = false;

    constructor(
        public signService: MockSignatureService,
        private router: Router,
        private cdr: ChangeDetectorRef
    ) {
        this.history = signService.getSigningHistory();
    }

    onDragOver(e: DragEvent) { e.preventDefault(); this.dragOver = true; }
    onDragLeave() { this.dragOver = false; }
    onDrop(e: DragEvent) {
        e.preventDefault(); this.dragOver = false;
        const file = e.dataTransfer?.files[0];
        if (file) this.selectFile(file);
    }

    onFileSelect(e: Event) {
        const input = e.target as HTMLInputElement;
        if (input.files?.[0]) this.selectFile(input.files[0]);
    }

    private selectFile(file: File) {
        this.selectedFile = file;
        this.fileName = file.name;
        this.cdr.markForCheck();
    }

    async startSigning() {
        if (!this.selectedFile) return;
        this.view.set('signing');
        this.cdr.markForCheck();

        const job = await this.signService.initiateSigning(this.fileName, this.params);
        // State machine runs in background via service — we watch currentJob signal
        // Wait for SIGNED status
        await this.waitForStatus('SIGNED');
        this.view.set('complete');
        this.history = this.signService.getSigningHistory();
        this.cdr.markForCheck();
    }

    private waitForStatus(target: string): Promise<void> {
        return new Promise(resolve => {
            const check = () => {
                const job = this.signService.currentJob();
                this.cdr.markForCheck();
                if (job?.status === target) { resolve(); return; }
                setTimeout(check, 300);
            };
            check();
        });
    }

    downloadSigned() {
        const job = this.signService.currentJob();
        if (!job) return;
        const blob = this.signService.getSignedDocument(job.jobId);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = `signed_${job.fileName}`; a.click();
        URL.revokeObjectURL(url);
    }

    reset() {
        this.signService.clearActiveJob();
        this.selectedFile = null;
        this.fileName = '';
        this.view.set('upload');
        this.cdr.markForCheck();
    }

    goBack() { this.router.navigate(['/dashboard']); }

    isStepDone(current: string, step: string): boolean {
        const order = ['PENDING', 'INITIATED', 'AWAITING_USER', 'CALLBACK_RECEIVED', 'COMPLETING', 'SIGNED'];
        return order.indexOf(current) > order.indexOf(step);
    }

    getStatusLabel(status: string): string {
        const map: Record<string, string> = {
            PENDING: 'Preparing…', INITIATED: 'Initiated', AWAITING_USER: 'Awaiting Approval',
            CALLBACK_RECEIVED: 'Processing…', COMPLETING: 'Applying LTV…', SIGNED: 'Signed ✓',
            FAILED: 'Failed', EXPIRED: 'Expired',
        };
        return map[status] || status;
    }
}
