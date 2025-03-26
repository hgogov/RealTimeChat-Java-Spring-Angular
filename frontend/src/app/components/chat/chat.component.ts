import { Component, OnInit, OnDestroy } from '@angular/core';
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
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subscription } from 'rxjs';
import { WebsocketService, ChatMessage } from '../../services/websocket.service';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';
import { MessageService } from '../../services/message.service';

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
    MatMenuModule
  ],
  templateUrl: './chat.component.html',  // External template
  styleUrls: ['./chat.component.scss']   // External styles
})
export class ChatComponent implements OnInit, OnDestroy {
  messageForm: FormGroup;
  messages: ChatMessage[] = [];
  currentRoom: string = 'general';
  currentUser: any = null;
  onlineUsers: {username: string, online: boolean}[] = [];
  isSomeoneTyping = false;
  typingUsername = '';

  private messagesSubscription!: Subscription;
  private userSubscription!: Subscription;
  private presenceSubscription!: Subscription;
  private typingSubscription!: Subscription;

  constructor(
    private fb: FormBuilder,
    private websocketService: WebsocketService,
    private authService: AuthService,
    private messageService: MessageService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    this.messageForm = this.fb.group({
      content: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.userSubscription = this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
    });

    this.messagesSubscription = this.websocketService.messages$.subscribe(messages => {
      this.messages = messages;
    });

    this.presenceSubscription = this.websocketService.presence$.subscribe(users => {
      this.onlineUsers = users;
    });

    this.typingSubscription = this.websocketService.typing$.subscribe(typingEvent => {
      if (typingEvent) {
        this.isSomeoneTyping = typingEvent.typing;
        this.typingUsername = typingEvent.username;
      } else {
        this.isSomeoneTyping = false;
      }
    });

    this.joinRoom(this.currentRoom);
    this.loadInitialMessages();
  }

  loadInitialMessages(): void {
    this.messageService.getMessages(this.currentRoom, 0, 20)
      .subscribe(messages => {
        this.messages = messages.reverse();
      });
  }

  joinRoom(roomId: string): void {
    this.currentRoom = roomId;
    this.websocketService.joinRoom(roomId);
    this.loadInitialMessages();
  }

  sendMessage(): void {
    if (this.messageForm.valid && this.currentUser) {
      const message: ChatMessage = {
        content: this.messageForm.value.content,
        sender: this.currentUser.username,
        roomId: this.currentRoom,
        timestamp: new Date().toISOString()
      };
      this.websocketService.sendMessage(message);
      this.messageForm.reset();
    }
  }

  onTyping(event: any): void {
    this.websocketService.sendTyping(event.target.value.length > 0);
  }

  ngOnDestroy(): void {
    this.messagesSubscription?.unsubscribe();
    this.userSubscription?.unsubscribe();
    this.presenceSubscription?.unsubscribe();
    this.typingSubscription?.unsubscribe();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
