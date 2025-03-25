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
  template: `
    <div class="chat-container">
      <mat-card class="chat-card">
        <mat-card-header>
          <mat-card-title>
            <div class="title-row">
              <span>Chat Room: {{currentRoom || 'General'}}</span>
              <div class="header-actions">
                <button mat-icon-button [matMenuTriggerFor]="menu">
                  <mat-icon>more_vert</mat-icon>
                </button>
                <mat-menu #menu="matMenu">
                  <button mat-menu-item (click)="joinRoom('general')">Join General Room</button>
                  <button mat-menu-item (click)="joinRoom('random')">Join Random Room</button>
                  <button mat-menu-item (click)="logout()">Logout</button>
                </mat-menu>
              </div>
            </div>
          </mat-card-title>
        </mat-card-header>

        <mat-card-content>
          <div class="messages-container">
            <mat-list role="list">
              <mat-list-item *ngFor="let message of messages" role="listitem" class="message-item">
                <div class="message" [ngClass]="{'my-message': message.sender === currentUser?.username}">
                  <div class="message-header">
                    <span class="sender">{{message.sender}}</span>
                    <span class="timestamp">{{message.timestamp | date:'short'}}</span>
                  </div>
                  <div class="message-content">
                    {{message.content}}
                  </div>
                </div>
              </mat-list-item>
            </mat-list>
          </div>

          <mat-divider></mat-divider>

          <form [formGroup]="messageForm" (ngSubmit)="sendMessage()" class="message-form">
            <mat-form-field appearance="outline" class="message-input">
              <mat-label>Message</mat-label>
              <input matInput formControlName="content" placeholder="Type a message...">
            </mat-form-field>
            <button mat-raised-button color="primary" type="submit" [disabled]="messageForm.invalid">
              <mat-icon>send</mat-icon>
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .chat-container {
      display: flex;
      justify-content: center;
      padding: 20px;
      height: 100vh;
      background-color: #f5f5f5;
    }

    .chat-card {
      width: 100%;
      max-width: 800px;
      height: 80vh;
      display: flex;
      flex-direction: column;
    }

    .title-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      width: 100%;
    }

    .header-actions {
      display: flex;
    }

    .messages-container {
      flex: 1;
      overflow-y: auto;
      padding: 10px;
      display: flex;
      flex-direction: column;
      height: calc(80vh - 200px);
    }

    .message-item {
      height: auto !important;
      margin-bottom: 8px;
    }

    .message {
      padding: 8px 12px;
      border-radius: 8px;
      background-color: #f1f1f1;
      max-width: 70%;
    }

    .my-message {
      background-color: #d1e7ff;
      margin-left: auto;
    }

    .message-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 4px;
      font-size: 0.8em;
    }

    .sender {
      font-weight: bold;
    }

    .timestamp {
      color: #666;
    }

    .message-content {
      word-break: break-word;
    }

    .message-form {
      display: flex;
      margin-top: 10px;
    }

    .message-input {
      flex: 1;
      margin-right: 10px;
    }
  `]
})
export class ChatComponent implements OnInit, OnDestroy {
  messageForm: FormGroup;
  messages: ChatMessage[] = [];
  currentRoom: string = 'general';
  currentUser: any = null;

  private messagesSubscription?: Subscription;
  private userSubscription?: Subscription;

  constructor(
    private formBuilder: FormBuilder,
    private websocketService: WebsocketService,
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    this.messageForm = this.formBuilder.group({
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

    // Join the default room
    this.joinRoom('general');
  }

  ngOnDestroy(): void {
    this.messagesSubscription?.unsubscribe();
    this.userSubscription?.unsubscribe();
    this.websocketService.disconnect();
  }

  joinRoom(roomId: string): void {
    this.currentRoom = roomId;
    this.websocketService.joinRoom(roomId);
    this.messages = []; // Clear messages when joining a new room
  }

  sendMessage(): void {
    if (this.messageForm.valid && this.currentUser) {
      const content = this.messageForm.value.content;

      const message: ChatMessage = {
        content: content,
        sender: this.currentUser.username,
        roomId: this.currentRoom
      };

      this.websocketService.sendMessage(message);
      this.messageForm.reset();
    }
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
