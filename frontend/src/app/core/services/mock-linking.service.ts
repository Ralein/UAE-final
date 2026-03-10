import { Injectable, signal } from '@angular/core';

export interface LinkStatus {
    linked: boolean;
    linkedAt: string | null;
    userType: string | null;
    uaepassUuid: string | null;
}

const LINK_KEY = 'uaepass_link_status';

@Injectable({ providedIn: 'root' })
export class MockLinkingService {
    private _linkStatus = signal<LinkStatus>(this.loadStatus());

    linkStatus = this._linkStatus.asReadonly();

    private loadStatus(): LinkStatus {
        const stored = localStorage.getItem(LINK_KEY);
        if (stored) {
            try { return JSON.parse(stored); } catch { /* ignore */ }
        }
        return { linked: true, linkedAt: new Date().toISOString(), userType: 'SOP1', uaepassUuid: 'uaepass-a1b2c3d4-e5f6-7890-abcd-ef1234567890' };
    }

    private persist(status: LinkStatus): void {
        localStorage.setItem(LINK_KEY, JSON.stringify(status));
        this._linkStatus.set(status);
    }

    async linkAccount(): Promise<LinkStatus> {
        await this.delay(2200);
        const status: LinkStatus = {
            linked: true,
            linkedAt: new Date().toISOString(),
            userType: 'SOP2',
            uaepassUuid: `uaepass-${this.uuid()}`,
        };
        this.persist(status);
        return status;
    }

    async unlinkAccount(): Promise<void> {
        await this.delay(1200);
        this.persist({ linked: false, linkedAt: null, userType: null, uaepassUuid: null });
    }

    getLinkStatus(): LinkStatus {
        return this._linkStatus();
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
