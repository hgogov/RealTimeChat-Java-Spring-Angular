import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subscription, Subject, takeUntil } from 'rxjs';

import { ChatRoomService, ChatRoom } from '../../services/chat-room.service';

@Component({
  selector: 'app-join-room-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatListModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatSnackBarModule
  ],
  templateUrl: './join-room-dialog.component.html',
  styleUrls: ['./join-room-dialog.component.scss']
})
export class JoinRoomDialogComponent implements OnInit, OnDestroy {
  isLoading = false;
  discoverableRooms: ChatRoom[] = [];
  joiningRoomId: number | null = null;
  private destroy$ = new Subject<void>();

  constructor(
    public dialogRef: MatDialogRef<JoinRoomDialogComponent>,
    private chatRoomService: ChatRoomService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadDiscoverableRooms();
  }

  ngOnDestroy(): void {
     this.destroy$.next();
     this.destroy$.complete();
  }


  loadDiscoverableRooms(): void {
    this.isLoading = true;
    this.chatRoomService.getDiscoverableRooms()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (rooms) => {
          this.discoverableRooms = rooms;
          this.isLoading = false;
        },
        error: (err) => {
          console.error("Error loading discoverable rooms:", err);
          this.snackBar.open('Could not load discoverable rooms.', 'Close', { duration: 3000 });
          this.isLoading = false;
        }
      });
  }

  onJoinClick(room: ChatRoom): void {
    if (this.joiningRoomId) return;

    this.joiningRoomId = room.id;
    this.chatRoomService.joinRoom(room.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open(`Successfully joined room "${room.name}"!`, 'Close', { duration: 3000 });
          this.joiningRoomId = null;
          this.dialogRef.close({ joined: true, joinedRoomName: room.name });
        },
        error: (err) => {
          console.error(`Error joining room ${room.name}:`, err);
          const errorMsg = err?.error?.message || 'Failed to join room.';
          this.snackBar.open(errorMsg, 'Close', { duration: 4000 });
          this.joiningRoomId = null;
        }
      });
  }

  onCancelClick(): void {
    this.dialogRef.close();
  }
}
