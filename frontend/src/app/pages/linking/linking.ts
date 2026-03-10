import { Component, signal, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MockLinkingService, LinkStatus } from '../../core/services/mock-linking.service';

type PageState = 'view' | 'linking' | 'unlinking' | 'confirm-unlink';

@Component({
    selector: 'app-linking',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './linking.html',
    styleUrl: './linking.css',
})
export class LinkingComponent {
    status = signal<LinkStatus>({ linked: false, linkedAt: null, userType: null, uaepassUuid: null });
    pageState = signal<PageState>('view');
    errorMsg = signal('');

    constructor(
        private linkService: MockLinkingService,
        private router: Router,
        private cdr: ChangeDetectorRef
    ) {
        this.status.set(this.linkService.getLinkStatus());
    }

    async startLinking() {
        this.pageState.set('linking');
        this.cdr.markForCheck();
        try {
            const result = await this.linkService.linkAccount();
            this.status.set(result);
            this.pageState.set('view');
            this.cdr.markForCheck();
        } catch {
            this.errorMsg.set('Linking failed. Please try again.');
            this.pageState.set('view');
            this.cdr.markForCheck();
        }
    }

    confirmUnlink() {
        this.pageState.set('confirm-unlink');
        this.cdr.markForCheck();
    }

    cancelUnlink() {
        this.pageState.set('view');
        this.cdr.markForCheck();
    }

    async doUnlink() {
        this.pageState.set('unlinking');
        this.cdr.markForCheck();
        await this.linkService.unlinkAccount();
        this.status.set(this.linkService.getLinkStatus());
        this.pageState.set('view');
        this.cdr.markForCheck();
    }

    goBack() {
        this.router.navigate(['/dashboard']);
    }

    get formattedDate(): string {
        const la = this.status().linkedAt;
        return la ? new Date(la).toLocaleString() : '';
    }
}
