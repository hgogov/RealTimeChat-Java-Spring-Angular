import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, FormGroup, Validators, ReactiveFormsModule, ValidationErrors, ValidatorFn } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';

import { ChatRoomService, CreateRoomPayload, ChatRoom } from '../../services/chat-room.service';

export function trimmedRequiredMinLengthValidator(minLength: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;

    if (value == null) {
        return null;
    }

    const trimmedValue = String(value).trim();

    if (trimmedValue.length === 0) {
        if (String(value).length > 0) {
            return { 'whitespace': true };
        } else {
            return null;
        }
    }

    if (trimmedValue.length < minLength) {
        return { 'minlength': { requiredLength: minLength, actualLength: trimmedValue.length } };
    }

    return null;
  };
}

@Component({
  selector: 'app-create-room-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSnackBarModule,
    MatSlideToggleModule,
    MatTooltipModule
  ],
  templateUrl: './create-room-dialog.component.html',
  styleUrls: ['./create-room-dialog.component.scss']
})
export class CreateRoomDialogComponent implements OnInit {
  roomForm: FormGroup;
  isSubmitting = false;

  constructor(
    private fb: FormBuilder,
    public dialogRef: MatDialogRef<CreateRoomDialogComponent>,
    private chatRoomService: ChatRoomService,
    private snackBar: MatSnackBar
  ) {
    this.roomForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(100), trimmedRequiredMinLengthValidator(3)]],
      isPublic: [true]
    });
  }

  ngOnInit(): void {
  }

  onCancelClick(): void {
    this.dialogRef.close();
  }

  onSubmit(): void {
    console.log('Form Value on Submit:', this.roomForm.value);
    if (this.roomForm.invalid || this.isSubmitting) {
      return;
    }

    this.isSubmitting = true;
    const roomPayload: CreateRoomPayload = {
      name: this.roomForm.value.name.trim(),
      isPublic: this.roomForm.value.isPublic
    };
    console.log('Payload to send:', roomPayload);

    this.chatRoomService.createRoom(roomPayload).subscribe({
      next: (createdRoom: ChatRoom) => {
        this.isSubmitting = false;
        this.snackBar.open(`Room "${createdRoom.name}" created! (${createdRoom.isPublic ? 'Public' : 'Private'})`, 'Close', { duration: 3000 });
        this.dialogRef.close({ roomCreated: true, newRoom: createdRoom });
      },
      error: (err: any) => {
        this.isSubmitting = false;
        console.error("Error creating room:", err);
        const errorMessage = err?.error?.message || err?.error || 'Failed to create room. Name might be taken.';
        this.snackBar.open(errorMessage, 'Close', { duration: 5000, panelClass: 'error-snackbar' });
      }
    });
  }
}
