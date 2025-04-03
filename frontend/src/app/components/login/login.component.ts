import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSnackBarModule
  ],
  template: `
    <div class="login-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Login</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="loginForm" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Username</mat-label>
              <input matInput formControlName="username" required>
              <mat-error *ngIf="loginForm.get('username')?.invalid">Please enter a username</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input matInput formControlName="password" type="password" required>
              <mat-error *ngIf="loginForm.get('password')?.invalid">Please enter a password</mat-error>
            </mat-form-field>

            <div class="button-row">
              <button mat-raised-button color="primary" type="submit" [disabled]="loginForm.invalid">Login</button>
              <button mat-button type="button" (click)="navigateToRegister()">Register</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 100vh;
      background-color: #f5f5f5;
    }

    mat-card {
      max-width: 400px;
      width: 100%;
      padding: 20px;
    }

    .full-width {
      width: 100%;
      margin-bottom: 15px;
    }

    .button-row {
      display: flex;
      justify-content: space-between;
      margin-top: 20px;
    }
  `]
})
export class LoginComponent {
  loginForm: FormGroup;

  constructor(
    private formBuilder: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    this.loginForm = this.formBuilder.group({
      username: ['', [Validators.required]],
      password: ['', [Validators.required]]
    });
  }

  onSubmit(): void {
    if (this.loginForm.valid) {
      const { username, password } = this.loginForm.value;

      this.authService.login(username, password)
        .subscribe({
          next: (response) => {
            console.log('Login successful!');
            this.router.navigate(['/chat']);
          },
          error: (err) => {
            console.error('Login error:', err);

            // Already handled in the service if status is 200
            if (err.status !== 200) {
              this.snackBar.open('Login failed. Please check your credentials.', 'Close', {
                duration: 3000
              });
            }
          }
        });
    }
  }

  navigateToRegister(): void {
    this.router.navigate(['/register']);
  }
}
