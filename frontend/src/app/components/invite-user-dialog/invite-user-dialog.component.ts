import { Component, Inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, takeUntil } from 'rxjs';

import { ChatRoomService, InviteUserPayload } from '../../services/chat-room.service';

export interface InviteUserDialogData {
  roomId: number;
  roomName: string;
}

@Component({
  selector: 'app-invite-user-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSnackBarModule, MatProgressSpinnerModule
  ],
  templateUrl: './invite-user-dialog.component.html',
  styleUrls: ['./invite-user-dialog.component.scss']
})
export class InviteUserDialogComponent implements OnInit, OnDestroy {
  inviteForm: FormGroup;
  isSubmitting = false;
  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    public dialogRef: MatDialogRef<InviteUserDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: InviteUserDialogData,
    private chatRoomService: ChatRoomService,
    private snackBar: MatSnackBar
  ) {
    this.inviteForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]]
    });
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onCancelClick(): void {
    this.dialogRef.close();
  }

  onSubmit(): void {
    if (this.inviteForm.invalid || this.isSubmitting) {
      return;
    }
    this.isSubmitting = true;
    const payload: InviteUserPayload = {
      username: this.inviteForm.value.username.trim()
    };

    this.chatRoomService.inviteUser(this.data.roomId, payload)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.isSubmitting = false;
          this.snackBar.open(`Invitation sent to '${payload.username}' for room '${this.data.roomName}'.`, 'Close', { duration: 3500 });
          this.dialogRef.close({ invited: true });
        },
        error: (err) => {
          this.isSubmitting = false;
          console.error("Error sending invitation:", err);
          const errorMsg = err?.error?.message || err?.error || 'Failed to send invitation.';
          this.snackBar.open(errorMsg, 'Close', { duration: 5000, panelClass: 'error-snackbar' });
        }
      });
  }
}
