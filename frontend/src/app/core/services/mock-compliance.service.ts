import { Injectable } from '@angular/core';
import { MockAuthService } from './mock-auth.service';

export interface DataSummary {
    profileFields: string[];
    signingJobsCount: number;
    faceVerificationsCount: number;
    auditLogEntries: number;
    dataRetentionDays: number;
}

export interface DataExport {
    exportedAt: string;
    profile: Record<string, string>;
    signingHistory: { jobId: string; fileName: string; status: string; date: string }[];
    faceVerifications: { id: string; purpose: string; status: string; date: string }[];
}

@Injectable({ providedIn: 'root' })
export class MockComplianceService {
    constructor(private auth: MockAuthService) { }

    async getDataSummary(): Promise<DataSummary> {
        await this.delay(800);
        return {
            profileFields: ['Full Name', 'Emirates ID', 'Email', 'Mobile', 'Nationality', 'Gender', 'Date of Birth', 'UAE PASS UUID'],
            signingJobsCount: this.countStorageItems('uaepass_signing_history'),
            faceVerificationsCount: this.countStorageItems('uaepass_face_history'),
            auditLogEntries: Math.floor(Math.random() * 50) + 10,
            dataRetentionDays: 365,
        };
    }

    async exportData(): Promise<DataExport> {
        await this.delay(1500);
        const profile = this.auth.getProfile();
        return {
            exportedAt: new Date().toISOString(),
            profile: profile ? {
                fullName: profile.fullnameEn,
                email: profile.email,
                mobile: profile.mobile,
                nationality: profile.nationalityEn,
                gender: profile.gender,
                dateOfBirth: profile.dob,
                userType: profile.userType,
                uaepassUuid: profile.uaepassUuid,
            } : {},
            signingHistory: this.getStorageItems('uaepass_signing_history').map((j: any) => ({
                jobId: j.jobId, fileName: j.fileName, status: j.status, date: j.createdAt,
            })),
            faceVerifications: this.getStorageItems('uaepass_face_history').map((v: any) => ({
                id: v.verificationId, purpose: v.purpose, status: v.step, date: v.createdAt,
            })),
        };
    }

    async deleteData(confirmation: string): Promise<{ success: boolean; deleted: string[] }> {
        if (confirmation !== 'DELETE_MY_DATA') {
            throw new Error('Invalid confirmation code. Type DELETE_MY_DATA to proceed.');
        }
        await this.delay(2000);

        const deleted: string[] = [];
        const keys = ['uaepass_signing_history', 'uaepass_face_history', 'uaepass_eseal_history', 'uaepass_hashsign_history', 'uaepass_link_status'];
        for (const key of keys) {
            if (localStorage.getItem(key)) {
                localStorage.removeItem(key);
                deleted.push(key.replace('uaepass_', '').replace('_', ' '));
            }
        }
        deleted.push('session data', 'profile (anonymized)');
        return { success: true, deleted };
    }

    private countStorageItems(key: string): number {
        const stored = localStorage.getItem(key);
        if (!stored) return 0;
        try { return JSON.parse(stored).length; } catch { return 0; }
    }

    private getStorageItems(key: string): any[] {
        const stored = localStorage.getItem(key);
        if (!stored) return [];
        try { return JSON.parse(stored); } catch { return []; }
    }

    private delay(ms: number): Promise<void> {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}
