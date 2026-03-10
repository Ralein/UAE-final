import { Component, signal, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MockComplianceService, DataSummary } from '../../core/services/mock-compliance.service';

type PageView = 'overview' | 'exporting' | 'deleting' | 'confirm-delete' | 'deleted';

@Component({
    selector: 'app-compliance',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './compliance.html',
    styleUrl: './compliance.css',
})
export class ComplianceComponent {
    view = signal<PageView>('overview');
    summary = signal<DataSummary | null>(null);
    deleteConfirmText = '';
    errorMsg = signal('');

    constructor(
        private complianceService: MockComplianceService,
        private router: Router,
        private cdr: ChangeDetectorRef
    ) {
        this.loadSummary();
    }

    private async loadSummary() {
        const s = await this.complianceService.getDataSummary();
        this.summary.set(s);
        this.cdr.markForCheck();
    }

    async exportData() {
        this.view.set('exporting');
        this.cdr.markForCheck();
        const data = await this.complianceService.exportData();
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'uaepass_data_export.json';
        a.click();
        URL.revokeObjectURL(url);
        this.view.set('overview');
        this.cdr.markForCheck();
    }

    showDeleteConfirm() {
        this.deleteConfirmText = '';
        this.errorMsg.set('');
        this.view.set('confirm-delete');
        this.cdr.markForCheck();
    }

    cancelDelete() {
        this.view.set('overview');
        this.cdr.markForCheck();
    }

    async confirmDelete() {
        if (this.deleteConfirmText !== 'DELETE_MY_DATA') {
            this.errorMsg.set('Please type DELETE_MY_DATA exactly to confirm.');
            this.cdr.markForCheck();
            return;
        }
        this.view.set('deleting');
        this.errorMsg.set('');
        this.cdr.markForCheck();
        try {
            await this.complianceService.deleteData(this.deleteConfirmText);
            this.view.set('deleted');
            this.cdr.markForCheck();
        } catch (e: any) {
            this.errorMsg.set(e.message || 'Deletion failed.');
            this.view.set('confirm-delete');
            this.cdr.markForCheck();
        }
    }

    goBack() {
        this.router.navigate(['/dashboard']);
    }
}
