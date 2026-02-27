import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardComponent implements OnInit {
  userProfile: any = null;
  loading: boolean = true;
  error: string | null = null;

  constructor(private http: HttpClient) { }

  ngOnInit() {
    this.http.get('http://localhost:8080/auth/me').subscribe({
      next: (data) => {
        this.userProfile = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load profile', err);
        this.error = 'Failed to load profile. Please log in again.';
        this.loading = false;
      }
    });
  }

  logout() {
    // Basic frontend logout redirect
    window.location.href = 'http://localhost:8080/auth/logout';
  }
}
