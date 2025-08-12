import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule, AsyncPipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, Subject, filter, distinctUntilChanged, takeUntil, debounceTime, timer } from 'rxjs';

// Material Modules
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatBadgeModule } from '@angular/material/badge';
import { MatExpansionModule } from '@angular/material/expansion';

// App Services and Interfaces
import { WebsocketService, ChatMessage, PresenceEvent, TypingEvent } from '../../services/websocket.service';
import { AuthService } from '../../services/auth.service';
import { MessageService } from '../../services/message.service';
import { ChatRoomService, ChatRoom } from '../../services/chat-room.service';
import { PresenceService } from '../../services/presence.service';
import { CreateRoomDialogComponent } from '../create-room-dialog/create-room-dialog.component';
import { JoinRoomDialogComponent } from '../join-room-dialog/join-room-dialog.component';
import { InviteUserDialogComponent, InviteUserDialogData } from '../invite-user-dialog/invite-user-dialog.component';
import { RoomInvitationService, RoomInvitation } from '../../services/room-invitation.service';
import { InvitationNotification } from '../../services/websocket.service';

// App Pipes
import { FilterCurrentUserPipe } from '../../pipes/filter-current-user.pipe';
import { RelativeTimePipe } from '../../pipes/relative-time.pipe';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AsyncPipe,
    FilterCurrentUserPipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatListModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
    MatSnackBarModule,
    MatDialogModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatBadgeModule,
    MatExpansionModule,
    RelativeTimePipe
  ],
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.scss']
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  messageForm: FormGroup;
  messages: ChatMessage[] = [];
  currentRoomName: string | null = null;
  currentUser: { username: string } | null = null;
  isSomeoneTyping = false;
  typingUsername = '';
  availableRooms: ChatRoom[] = [];
  pendingInvitations: RoomInvitation[] = [];
  hasUnreadInvitations = false;

  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;
  private shouldScrollToBottom = true;
  private destroy$ = new Subject<void>();
  private typingTimeout: any;

  private fb = inject(FormBuilder);
  public websocketService = inject(WebsocketService);
  private authService = inject(AuthService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private snackBar = inject(MatSnackBar);
  private cdRef = inject(ChangeDetectorRef);
  private dialog = inject(MatDialog);
  private chatRoomService = inject(ChatRoomService);
  private presenceService = inject(PresenceService);


  constructor(private roomInvitationService: RoomInvitationService) {
    this.messageForm = this.fb.group({
      content: ['']
    });
  }

  ngOnInit(): void {
    this.setupUserSubscription();
    this.setupTypingSubscription();
    this.setupRoomSubscription();
    this.setupMessageSubscription();
    this.loadUserRooms();
    this.setupInvitationNotificationSubscription();
    this.loadPendingInvitations();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.typingTimeout) {
      clearTimeout(this.typingTimeout);
    }
  }

  // --- Invitation Handling Logic ---
  private setupInvitationNotificationSubscription(): void {
      this.websocketService.invitationNotification$
          .pipe(takeUntil(this.destroy$))
          .subscribe((notification: InvitationNotification) => {
              console.log('[ChatComponent] Received invitation notification:', notification);
              if (notification.type === 'NEW_INVITATION') {
                  this.snackBar.open(`You have a new invitation to join room: ${notification.roomName}!`, 'View', { duration: 7000 })
                      .onAction().subscribe(() => {
                          console.log('View invitations action clicked');
                      });
                  this.hasUnreadInvitations = true;
                  this.loadPendingInvitations();
              }
          });
  }

  loadPendingInvitations(): void {
      console.log('[ChatComponent] Loading pending invitations...');
      this.roomInvitationService.getPendingInvitations()
          .pipe(takeUntil(this.destroy$))
          .subscribe({
              next: (invites) => {
                  this.pendingInvitations = invites;
                  this.hasUnreadInvitations = invites.length > 0;
                  console.log('[ChatComponent] Pending invitations loaded:', this.pendingInvitations);
                  this.cdRef.detectChanges();
              },
              error: (err) => {
                  console.error('[ChatComponent] Error loading pending invitations:', err);
                  this.snackBar.open('Failed to load your invitations.', 'Close', { duration: 3000 });
              }
          });
  }

  acceptInvitation(invitation: RoomInvitation): void {
      console.log('[ChatComponent] Accepting invitation:', invitation);
      this.roomInvitationService.acceptInvitation(invitation.id)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
              next: () => {
                  this.snackBar.open(`You joined room "${invitation.roomName}"!`, 'OK', { duration: 3000 });
                  this.loadPendingInvitations();
                  this.loadUserRooms();
                  timer(100).pipe(takeUntil(this.destroy$)).subscribe(() => {
                      this.selectRoom(invitation.roomName);
                  });
              },
              error: (err) => {
                  console.error('[ChatComponent] Error accepting invitation:', err);
                  this.snackBar.open(err?.error?.message || 'Failed to accept invitation.', 'Close', { duration: 4000 });
              }
          });
  }

  declineInvitation(invitation: RoomInvitation): void {
      console.log('[ChatComponent] Declining invitation:', invitation);
      this.roomInvitationService.declineInvitation(invitation.id)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
              next: () => {
                  this.snackBar.open(`Invitation to "${invitation.roomName}" declined.`, 'OK', { duration: 3000 });
                  this.loadPendingInvitations();
              },
              error: (err) => {
                  console.error('[ChatComponent] Error declining invitation:', err);
                  this.snackBar.open(err?.error?.message || 'Failed to decline invitation.', 'Close', { duration: 4000 });
              }
          });
  }


  // --- Setup Methods ---

  private setupUserSubscription(): void {
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.currentUser = user;
        this.cdRef.detectChanges();
      });
  }

  private setupTypingSubscription(): void {
      this.websocketService.typing$
        .pipe(
          takeUntil(this.destroy$),
          filter((event: TypingEvent | null): event is TypingEvent =>
            !!event &&
            event.roomId === this.currentRoomName &&
            event.username !== this.currentUser?.username
          ),
          distinctUntilChanged((prev, curr) =>
            prev.username === curr.username && prev.typing === curr.typing
          ),
        )
        .subscribe((event: TypingEvent) => {
           this.isSomeoneTyping = event.typing;
           this.typingUsername = event.typing ? event.username : '';
           this.cdRef.detectChanges();
        });
  }

  private setupRoomSubscription(): void {
    this.websocketService.currentRoom$
        .pipe(takeUntil(this.destroy$))
        .subscribe(roomName => {
            const previousRoom = this.currentRoomName;
            this.currentRoomName = roomName;
            console.log(`[ChatComponent] Current room changed to: ${roomName}`);
            if (roomName && roomName !== previousRoom) {
                this.messages = [];
                this.isSomeoneTyping = false;
                this.typingUsername = '';
                this.loadInitialMessages(roomName);
                this.loadInitialRoomPresence(roomName);
            } else if (!roomName) {
                 this.messages = [];
                 this.websocketService.setCurrentRoomPresence([]);
            }
             this.cdRef.detectChanges();
        });
  }

  private setupMessageSubscription(): void {
        this.websocketService.messages$
            .pipe(
                takeUntil(this.destroy$),
                filter(msg => !!msg && msg.roomId === this.currentRoomName)
            )
            .subscribe({
                next: (newMessage: ChatMessage) => {
                     console.log('[ChatComponent] Received NEW message for current room:', newMessage);
                    if (!this.messages.some(m => m.id === newMessage.id && m.timestamp === newMessage.timestamp)) {
                        this.messages = [...this.messages, newMessage];
                        this.shouldScrollToBottom = true;
                        this.cdRef.detectChanges();
                    } else {
                         console.log('[ChatComponent] Duplicate message ignored:', newMessage.id);
                    }
                },
                error: (err: any) => {
                    console.error('[ChatComponent] Error on websocket messages$ stream:', err);
                    this.snackBar.open('Error receiving messages', 'Close', { duration: 3000 });
                 }
            });
  }

  // --- Data Loading ---

  loadUserRooms(): void {
      console.log('[ChatComponent] Loading user rooms...');
      this.chatRoomService.getUserRooms()
          .pipe(takeUntil(this.destroy$))
          .subscribe({
              next: (rooms) => {
                  console.log('[ChatComponent] Received user rooms:', rooms);
                  const previouslySelectedRoom = this.currentRoomName;
                  this.availableRooms = rooms.sort((a, b) => a.name.localeCompare(b.name));

                  const currentRoomStillExists = previouslySelectedRoom && rooms.some(r => r.name === previouslySelectedRoom);

                  if (currentRoomStillExists) {
                       console.log(`[ChatComponent] Current room '${previouslySelectedRoom}' still exists.`);
                  } else if (rooms.length > 0) {
                      const roomToJoin = rooms.find(r => r.name.toLowerCase() === 'general') || rooms[0];
                      console.log(`[ChatComponent] Selecting default/first room: '${roomToJoin.name}'`);
                       timer(100).pipe(takeUntil(this.destroy$)).subscribe(() => {
                          this.selectRoom(roomToJoin.name);
                       });
                  } else {
                       console.log('[ChatComponent] User is not in any rooms.');
                       if (this.currentRoomName) {
                           this.websocketService.leaveCurrentRoom();
                       }
                  }
                  this.cdRef.detectChanges();
              },
              error: (err: any) => {
                  console.error('[ChatComponent] Error loading user rooms:', err);
                  this.snackBar.open('Failed to load your chat rooms', 'Close', { duration: 3000 });
              }
          });
  }

  loadInitialMessages(roomId: string): void {
    if (!roomId) return;
    console.log(`[ChatComponent] loadInitialMessages called for room: ${roomId}`);
    this.shouldScrollToBottom = true;

    this.messageService.getMessages(roomId, 0, 50)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (messagesPage: any) => {
            const messagesArray = Array.isArray(messagesPage?.content) ? messagesPage.content :
                                  Array.isArray(messagesPage) ? messagesPage : [];

            if (messagesArray.length > 0) {
                this.messages = [...messagesArray].sort((a, b) =>
                    new Date(a.timestamp!).getTime() - new Date(b.timestamp!).getTime()
                );
            } else {
                this.messages = [];
            }
            this.shouldScrollToBottom = true;
            this.cdRef.detectChanges();
            timer(0).pipe(takeUntil(this.destroy$)).subscribe(() => this.scrollToBottom());
        },
        error: (err: any) => {
          this.snackBar.open('Failed to load messages', 'Close', { duration: 3000 });
          console.error(`[ChatComponent] Error loading messages for room ${roomId}:`, err);
        }
      });
  }

  loadInitialRoomPresence(roomName: string): void {
    if (!roomName) return;
    console.log(`[ChatComponent] Loading initial presence for room: ${roomName}`);

    const room = this.availableRooms.find(r => r.name === roomName);
    if (!room) {
        console.error(`[ChatComponent] Cannot load presence: Room ID not found for room name '${roomName}'`);
        this.websocketService.setCurrentRoomPresence([]);
        return;
    }
    const roomId = room.id;

    this.presenceService.getOnlineMembers(roomId)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
            next: (onlineUsernames: string[]) => {
                console.log(`[ChatComponent] Received initial online members for room ${roomName}:`, onlineUsernames);
                const initialPresenceList = onlineUsernames
                    .map(username => ({ username: username, online: true }));
                this.websocketService.setCurrentRoomPresence(initialPresenceList);
            },
            error: (err) => {
                console.error(`[ChatComponent] Error loading initial presence for room ${roomName}:`, err);
                this.snackBar.open(`Failed to load online users for ${roomName}`, 'Close', { duration: 3000 });
                 this.websocketService.setCurrentRoomPresence([]); // Clear presence in service on error
            }
        });
  }


  // --- UI Actions ---

  selectRoom(roomName: string | null): void {
      if (roomName && this.availableRooms.some(r => r.name === roomName)) {
          if (roomName !== this.currentRoomName) {
             console.log(`[ChatComponent] Selecting room: ${roomName}`);
             this.websocketService.joinRoom(roomName);
          }
      } else if (roomName === null && this.currentRoomName) {
           console.log('[ChatComponent] Deselecting room (clearing selection).');
           this.websocketService.leaveCurrentRoom();
      } else if (roomName) {
           console.warn(`[ChatComponent] Attempted to select room '${roomName}' which is not in the available list.`);
      }
  }

  sendMessage(): void {
    if (this.messageForm.invalid || !this.currentUser || !this.currentRoomName) return;
    const messageContent = this.messageForm.value.content?.trim();
    if (!messageContent) return;

    if (this.typingTimeout) clearTimeout(this.typingTimeout);
    this.websocketService.sendTyping(false);
    this.isSomeoneTyping = false;

    const message: Omit<ChatMessage, 'sender' | 'timestamp' | 'id' | 'roomId'> = {
      content: messageContent,
    };

    this.websocketService.sendMessage(message)
        .then(() => { this.messageForm.reset(); })
        .catch((err: any) => {
             this.snackBar.open('Failed to send message', 'Close', { duration: 3000 });
             console.error('[ChatComponent] Error sending message:', err);
        });
  }

  onMessageInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const isTyping = input.value.length > 0;
    this.websocketService.sendTyping(isTyping);
    if (this.typingTimeout) clearTimeout(this.typingTimeout);
    if (isTyping) {
      this.typingTimeout = setTimeout(() => { this.websocketService.sendTyping(false); }, 2000);
    }
  }

  openCreateRoomDialog(): void {
      const dialogRef = this.dialog.open(CreateRoomDialogComponent, { width: '350px', disableClose: true });
      dialogRef.afterClosed().pipe(takeUntil(this.destroy$)).subscribe(result => {
          console.log('Create Room Dialog closed with result:', result);
          if (result?.roomCreated && result?.newRoom) {
              this.loadUserRooms();
              timer(200).pipe(takeUntil(this.destroy$)).subscribe(() => {
                   if (this.availableRooms.some(r => r.name === result.newRoom.name)) {
                     this.selectRoom(result.newRoom.name);
                   } else {
                     console.warn(`Newly created room '${result.newRoom.name}' not found in list after reload.`);
                   }
              });
          }
      });
  }

  openJoinRoomDialog(): void {
      const dialogRef = this.dialog.open(JoinRoomDialogComponent, { width: '450px' });
      dialogRef.afterClosed().pipe(takeUntil(this.destroy$)).subscribe(result => {
          console.log('Join Room Dialog closed with result:', result);
          if (result?.joined && result?.joinedRoomName) {
              this.snackBar.open(`Joined room "${result.joinedRoomName}"`, 'Close', { duration: 3000 });
              this.loadUserRooms();
              timer(100).pipe(takeUntil(this.destroy$)).subscribe(() => {
                  this.selectRoom(result.joinedRoomName);
              });
          }
      });
  }

   confirmLeaveRoom(roomId: number, roomName: string): void {
        this.leaveRoom(roomId, roomName);
   }

    private leaveRoom(roomId: number, roomName: string): void {
        console.log(`[ChatComponent] Attempting to leave room ID: ${roomId} (${roomName})`);
        this.chatRoomService.leaveRoom(roomId)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: () => {
                    this.snackBar.open(`You have left room "${roomName}"`, 'Close', { duration: 3000 });
                    this.loadUserRooms();
                },
                error: (err: any) => {
                     console.error(`[ChatComponent] Error leaving room ${roomId}:`, err);
                     const errorMsg = err?.error?.message || 'Failed to leave room.';
                     this.snackBar.open(errorMsg, 'Close', { duration: 4000 });
                }
            });
    }

    openInviteUserDialog(roomId: number, roomName: string): void {
        const dialogRef = this.dialog.open<InviteUserDialogComponent, InviteUserDialogData>(
            InviteUserDialogComponent, {
            width: '400px',
            data: { roomId, roomName }
        });

        dialogRef.afterClosed()
            .pipe(takeUntil(this.destroy$))
            .subscribe(result => {
                if (result?.invited) {
                    console.log(`Invitation process completed for room '${roomName}'.`);
                }
            });
    }


  logout(): void {
    this.authService.logout();
    this.websocketService.disconnect();
    this.router.navigate(['/login']);
  }

  // --- Lifecycle Hooks & Helpers ---

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
        this.scrollToBottom();
        this.shouldScrollToBottom = false;
    }
  }

  private scrollToBottom(): void {
    if (this.messagesContainer?.nativeElement) {
        try {
            const element = this.messagesContainer.nativeElement;
            element.scrollTop = element.scrollHeight;
        } catch (err) {
            console.error("[ChatComponent] Could not scroll to bottom:", err);
        }
    }
  }

  trackByMessageId(index: number, message: ChatMessage): number | string | undefined {
    return message.id ?? message.timestamp;
  }
}
