import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { DashboardComponent } from './pages/dashboard/dashboard';
import { LinkingComponent } from './pages/linking/linking';
import { SignatureComponent } from './pages/signature/signature';
import { EsealComponent } from './pages/eseal/eseal';
import { HashsignComponent } from './pages/hashsign/hashsign';
import { FaceVerifyComponent } from './pages/face-verify/face-verify';
import { ComplianceComponent } from './pages/compliance/compliance';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', component: LoginComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'linking', component: LinkingComponent, canActivate: [authGuard] },
  { path: 'signature', component: SignatureComponent, canActivate: [authGuard] },
  { path: 'eseal', component: EsealComponent, canActivate: [authGuard] },
  { path: 'hashsign', component: HashsignComponent, canActivate: [authGuard] },
  { path: 'face-verify', component: FaceVerifyComponent, canActivate: [authGuard] },
  { path: 'compliance', component: ComplianceComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' }
];
