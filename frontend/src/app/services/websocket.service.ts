import { Injectable } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

export interface ChatMessage {
  id?: number;
  content: string;
  sender: string;
  roomId: string;
  timestamp?: string;
}

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private client!: Client;
  private messagesSubject = new BehaviorSubject<ChatMessage[]>([]);
  public messages$ = this.messagesSubject.asObservable();

  private presenceSubject = new BehaviorSubject<{username: string, online: boolean}[]>([]);
  public presence$ = this.presenceSubject.asObservable();

  private typingSubject = new BehaviorSubject<{username: string, typing: boolean} | null>(null);
  public typing$ = this.typingSubject.asObservable();

  private roomSubscription?: StompSubscription;
  private currentRoom: string = '';

  constructor(private authService: AuthService) {
    this.initializeWebSocketClient();
  }

  private initializeWebSocketClient(): void {
    this.client = new Client({
      brokerURL: environment.wsUrl,
      connectHeaders: {},
      debug: (str) => console.log('STOMP: ' + str),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000
    });

    this.client.onConnect = () => {
      console.log('Connected to WebSocket');
      this.initializePresenceTracking();
    };

    this.client.onStompError = (frame) => {
      console.error('STOMP error:', frame);
    };

    this.client.activate();
  }

  private initializePresenceTracking(): void {
    this.client.subscribe('/topic/presence', (message: IMessage) => {
      const { username, status } = JSON.parse(message.body);
      const current = this.presenceSubject.value.filter(u => u.username !== username);
      this.presenceSubject.next([...current, { username, online: status === 'online' }]);
    });
  }

  joinRoom(roomId: string): void {
    if (this.roomSubscription) {
      this.roomSubscription.unsubscribe();
    }

    this.currentRoom = roomId;
    this.messagesSubject.next([]);

    this.roomSubscription = this.client.subscribe(
      `/topic/chat/${roomId}`,
      (message: IMessage) => {
        const chatMessage: ChatMessage = JSON.parse(message.body);
        const messages = this.messagesSubject.value;
        this.messagesSubject.next([...messages, chatMessage]);
      }
    );

    this.client.subscribe(
      `/topic/typing/${roomId}`,
      (message: IMessage) => {
        this.typingSubject.next(JSON.parse(message.body));
      }
    );
  }

  sendMessage(message: ChatMessage): void {
    if (this.client.connected) {
      this.client.publish({
        destination: '/app/chat.sendMessage',
        body: JSON.stringify(message)
      });
    } else {
      console.error('WebSocket not connected');
    }
  }

  sendTyping(isTyping: boolean): void {
    if (this.client.connected && this.currentRoom) {
      this.client.publish({
        destination: '/app/chat.typing',
        body: JSON.stringify({
          roomId: this.currentRoom,
          username: this.authService.currentUserValue?.username,
          typing: isTyping
        })
      });
    }
  }

  disconnect(): void {
    if (this.client) {
      this.client.deactivate();
    }
  }
}
