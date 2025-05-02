import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
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

// App Services and Interfaces
import { WebsocketService, ChatMessage, PresenceEvent, TypingEvent } from '../../services/websocket.service';
import { AuthService } from '../../services/auth.service';
import { MessageService } from '../../services/message.service';
import { ChatRoomService, ChatRoom } from '../../services/chat-room.service';
import { CreateRoomDialogComponent } from '../create-room-dialog/create-room-dialog.component';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
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
  ],
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.scss']
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  messageForm: FormGroup;
  messages: ChatMessage[] = [];
  currentRoomName: string | null = null;
  currentUser: { username: string } | null = null;
  onlineUsers: PresenceEvent[] = [];
  isSomeoneTyping = false;
  typingUsername = '';
  availableRooms: ChatRoom[] = [];

  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;
  private shouldScrollToBottom = true;
  private destroy$ = new Subject<void>();
  private typingTimeout: any;

  constructor(
    private fb: FormBuilder,
    private websocketService: WebsocketService,
    private authService: AuthService,
    private messageService: MessageService,
    private router: Router,
    private snackBar: MatSnackBar,
    private cdRef: ChangeDetectorRef,
    private dialog: MatDialog,
    private chatRoomService: ChatRoomService
  ) {
    this.messageForm = this.fb.group({
      content: ['']
    });
  }

  ngOnInit(): void {
    this.setupUserSubscription();
    this.setupPresenceSubscription();
    this.setupTypingSubscription();
    this.setupRoomSubscription();
    this.setupMessageSubscription();
    this.loadUserRooms();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.typingTimeout) {
      clearTimeout(this.typingTimeout);
    }
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

  private setupPresenceSubscription(): void {
    this.websocketService.presence$
      .pipe(takeUntil(this.destroy$))
      .subscribe((users: PresenceEvent[]) => {
        const currentUsername = this.currentUser?.username;
        this.onlineUsers = users.filter(u => u.username !== currentUsername);
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
            } else if (!roomName) {
                 this.messages = [];
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
                      console.log(`[ChatComponent] Current room '${previouslySelectedRoom}' gone or none selected. Selecting default/first: '${roomToJoin.name}'`);
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
            timer(0).subscribe(() => this.scrollToBottom());
        },
        error: (err: any) => {
          this.snackBar.open('Failed to load messages', 'Close', { duration: 3000 });
          console.error('[ChatComponent] Error loading messages:', err);
        }
      });
  }

  // --- UI Actions ---

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
                  if (this.currentRoomName === roomName) {
                      this.websocketService.leaveCurrentRoom();
                  }
                  this.loadUserRooms();
              },
              error: (err) => {
                   console.error(`[ChatComponent] Error leaving room ${roomId}:`, err);
                   const errorMsg = err?.error?.message || 'Failed to leave room.';
                   this.snackBar.open(errorMsg, 'Close', { duration: 4000 });
              }
          });
  }

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
        .then(() => {
             this.messageForm.reset();
        })
        .catch((err: any) => {
             this.snackBar.open('Failed to send message', 'Close', { duration: 3000 });
             console.error('[ChatComponent] Error sending message:', err);
        });
  }

  // Handle user typing input
  onMessageInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const isTyping = input.value.length > 0;

    this.websocketService.sendTyping(isTyping);

    // Debounce sending 'stop typing'
    if (this.typingTimeout) clearTimeout(this.typingTimeout);

    if (isTyping) {
      this.typingTimeout = setTimeout(() => {
        this.websocketService.sendTyping(false); // Send stop typing after delay
      }, 2000); // 2 seconds inactivity threshold
    }
  }

  openCreateRoomDialog(): void {
      const dialogRef = this.dialog.open(CreateRoomDialogComponent, {
          width: '350px',
          disableClose: true
      });
      dialogRef.afterClosed()
        .pipe(takeUntil(this.destroy$))
        .subscribe(result => {
          console.log('Create Room Dialog closed with result:', result);
          if (result?.roomCreated && result?.newRoom) {
              this.loadUserRooms();
              timer(200).pipe(takeUntil(this.destroy$)).subscribe(() => {
                  this.selectRoom(result.newRoom.name);
              });
          }
      });
  }

  logout(): void {
    this.authService.logout();
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
