import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked, ChangeDetectorRef, ApplicationRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subscription, Subject } from 'rxjs';
import { WebsocketService, ChatMessage, PresenceEvent, TypingEvent } from '../../services/websocket.service';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';
import { MessageService } from '../../services/message.service';
import { filter, distinctUntilChanged, debounceTime, takeUntil, skip } from 'rxjs/operators';

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
    MatSnackBarModule
  ],
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.scss']
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  messageForm: FormGroup;
  messages: ChatMessage[] = [];
  currentRoom: string = 'general';
  currentUser: { username: string } | null = null;
  onlineUsers: PresenceEvent[] = [];
  isSomeoneTyping = false;
  typingUsername = '';

  // For scrolling
  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;
  private shouldScrollToBottom = true;

  private destroy$ = new Subject<void>();
  private typingTimeout: any;
  private roomMessagesSubscription?: Subscription;

  constructor(
    private fb: FormBuilder,
    private websocketService: WebsocketService,
    private authService: AuthService,
    private messageService: MessageService,
    private router: Router,
    private snackBar: MatSnackBar,
    private cdRef: ChangeDetectorRef,
    private appRef: ApplicationRef
  ) {
    this.messageForm = this.fb.group({
      content: ['']
    });
  }

  ngOnInit(): void {
    this.setupUserSubscription();
    this.setupPresenceSubscription();
    this.setupTypingSubscription();
    this.joinRoom(this.currentRoom); // Join room initializes message loading and subscription
  }

  // --- Scrolling Logic ---
  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
        this.scrollToBottom();
        this.shouldScrollToBottom = false;
    }
  }

  private scrollToBottom(): void {
    try {
        this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
    } catch (err) {
        console.error("Could not scroll to bottom:", err);
    }
  }
  // --- End Scrolling Logic ---


  /* Typing Indicator Methods */
  onTyping(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.websocketService.sendTyping(input.value.length > 0);
  }

  trackByMessageId(index: number, message: ChatMessage): number | string {
    return message.id ?? message.timestamp ?? index;
  }

  onMessageInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const isTyping = input.value.length > 0;

    // Send typing status immediately
    this.websocketService.sendTyping(isTyping);

    // Debounce sending 'stop typing'
    if (this.typingTimeout) {
      clearTimeout(this.typingTimeout);
    }

    if (isTyping) {
      this.typingTimeout = setTimeout(() => {
        this.websocketService.sendTyping(false);
      }, 2000); // Send 'stop typing' after 2 seconds of inactivity
    }
  }

  /* Core Chat Methods */
  private setupUserSubscription(): void {
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.currentUser = user;
      });
  }

  private setupPresenceSubscription(): void {
    this.websocketService.presence$
      .pipe(takeUntil(this.destroy$))
      .subscribe((users: PresenceEvent[]) => {
        console.log('[ChatComponent] Received presence update via observable:', users);
        this.onlineUsers = users;
        this.cdRef.detectChanges();
      });
  }

  private setupTypingSubscription(): void {
    this.websocketService.typing$
      .pipe(
        takeUntil(this.destroy$),
        filter((event: TypingEvent | null): event is TypingEvent =>
          !!event &&
          event.roomId === this.currentRoom &&
          event.username !== this.currentUser?.username // Ignore self-typing
        ),
        distinctUntilChanged((prev, curr) => // Only emit if typing status or user changes
          prev.username === curr.username && prev.typing === curr.typing
        ),
      )
      .subscribe((event: TypingEvent) => {
         this.isSomeoneTyping = event.typing;
         this.typingUsername = event.typing ? event.username : '';
      });
  }

  loadInitialMessages(): void {
    console.log(`[ChatComponent] loadInitialMessages called for room: ${this.currentRoom}`);
    this.messages = [];
    this.shouldScrollToBottom = true;

    this.messageService.getMessages(this.currentRoom, 0, 50)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (messagesPage: any) => {
            console.log('[ChatComponent] Received historical messages response:', messagesPage);

            const messagesArray = Array.isArray(messagesPage?.content) ? messagesPage.content :
                                  Array.isArray(messagesPage) ? messagesPage : [];

            console.log('[ChatComponent] Extracted messages array:', messagesArray);

            if (messagesArray.length > 0) {
                this.messages = [...messagesArray].sort((a, b) =>
                    new Date(a.timestamp!).getTime() - new Date(b.timestamp!).getTime()
                );
                console.log('[ChatComponent] Sorted messages:', this.messages);
            } else {
                this.messages = [];
                console.log('[ChatComponent] No historical messages found.');
            }
            this.shouldScrollToBottom = true;
            this.cdRef.detectChanges();
        },
        error: (err: any) => {
          this.snackBar.open('Failed to load messages', 'Close', { duration: 3000 });
          console.error('[ChatComponent] Error loading messages:', err);
        }
      });
  }

  joinRoom(roomId: string): void {
    console.log(`[ChatComponent] joinRoom called for: ${roomId}`);
    // 1. Unsubscribe from previous room's message stream if exists
    if (this.roomMessagesSubscription) {
      this.roomMessagesSubscription.unsubscribe();
    }

    this.currentRoom = roomId;
    this.messages = []; // Clear messages immediately for UI responsiveness
    this.isSomeoneTyping = false; // Reset typing indicator
    this.typingUsername = '';

    // 2. Tell the service to join the new room (sets up internal STOMP subs)
    try {
       this.websocketService.joinRoom(roomId);
    } catch (error) {
        this.snackBar.open('Failed to initiate joining room', 'Close', { duration: 3000 });
        console.error('[ChatComponent] Error calling websocketService.joinRoom:', error);
        return;
    }

    // 3. Load initial/historical messages for the new room
    this.loadInitialMessages();

    // 4. Subscribe to NEW messages from WebSocket
     this.roomMessagesSubscription = this.websocketService.messages$
       .pipe(
           takeUntil(this.destroy$),
           filter(msg => !!msg)
       )
       .subscribe({
         next: (newMessage: ChatMessage) => {
           console.log('[ChatComponent] Received NEW message via WebSocket:', newMessage);
           // Avoid adding duplicates if message already loaded via history
           if (!this.messages.some(m => m.id === newMessage.id)) {
                this.messages = [...this.messages, newMessage];
                this.shouldScrollToBottom = true;
                this.cdRef.detectChanges();
           } else {
                console.log('[ChatComponent] Duplicate message ignored:', newMessage.id);
           }
         },
         error: (err: any) => {
           this.snackBar.open('Error receiving messages', 'Close', { duration: 3000 });
           console.error('[ChatComponent] Error on websocket messages$ stream:', err);
         }
       });
  }

  sendMessage(): void {
    if (this.messageForm.invalid || !this.currentUser) return;

    // Clear local typing indicator immediately
    if (this.typingTimeout) {
      clearTimeout(this.typingTimeout);
      this.typingTimeout = null;
    }
     // Send 'stop typing' immediately when sending a message
    this.websocketService.sendTyping(false);


    const messageContent = this.messageForm.value.content.trim();
    if (!messageContent) return; // Don't send empty messages

    const message: Omit<ChatMessage, 'sender' | 'timestamp' | 'id'> = {
      content: messageContent,
      roomId: this.currentRoom,
    };

    try {
        this.websocketService.sendMessage(message);
        this.messageForm.reset();
    } catch(error) {
         this.snackBar.open('Failed to send message', 'Close', { duration: 3000 });
         console.error('Error sending message:', error);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();

    if (this.roomMessagesSubscription) {
      this.roomMessagesSubscription.unsubscribe();
    }

    if (this.typingTimeout) {
      clearTimeout(this.typingTimeout);
    }
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
