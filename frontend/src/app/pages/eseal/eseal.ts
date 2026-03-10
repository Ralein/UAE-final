import { Component, signal, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MockESealService, ESealJob, ESealVerifyResult, SealType } from '../../core/services/mock-eseal.service';

@Component({
    selector: 'app-eseal',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './eseal.html',
    styleUrl: './eseal.css',
})
export class EsealComponent {
    sealType = signal<SealType>('PADES');
    view = signal<'upload' | 'processing' | 'result' | 'verify'>('upload');
    selectedFile: File | null = null;
    fileName = '';
    verifyResult = signal<ESealVerifyResult | null>(null);
    history: ESealJob[] = [];

    constructor(public esealService: MockESealService, private router: Router, private cdr: ChangeDetectorRef) {
        this.history = esealService.getSealHistory();
    }

    setSealType(type: SealType) { this.sealType.set(type); this.cdr.markForCheck(); }

    onFileSelect(e: Event) {
        const input = e.target as HTMLInputElement;
        if (input.files?.[0]) { this.selectedFile = input.files[0]; this.fileName = input.files[0].name; this.cdr.markForCheck(); }
    }

    async startSeal() {
        if (!this.selectedFile) return;
        this.view.set('processing');
        this.cdr.markForCheck();
        if (this.sealType() === 'PADES') {
            await this.esealService.sealPdf(this.fileName);
        } else {
            await this.esealService.sealDocument(this.fileName);
        }
        this.history = this.esealService.getSealHistory();
        this.view.set('result');
        this.cdr.markForCheck();
    }

    async startVerify() {
        if (!this.selectedFile) return;
        this.view.set('processing');
        this.cdr.markForCheck();
        const result = await this.esealService.verifySeal(this.fileName);
        this.verifyResult.set(result);
        this.view.set('verify');
        this.cdr.markForCheck();
    }

    downloadSealed() {
        const job = this.esealService.currentJob();
        if (!job) return;
        const blob = this.esealService.getSealedDocument(job.jobId);
        const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = `sealed_${job.fileName}`; a.click();
    }

    reset() {
        this.esealService.clearActiveJob(); this.selectedFile = null; this.fileName = '';
        this.verifyResult.set(null); this.view.set('upload'); this.cdr.markForCheck();
    }

    goBack() { this.router.navigate(['/dashboard']); }
}
