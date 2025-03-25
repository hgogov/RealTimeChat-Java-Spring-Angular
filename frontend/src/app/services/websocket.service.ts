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

  private roomSubscription?: StompSubscription;
  private currentRoom: string = '';

  constructor(private authService: AuthService) {
    this.initializeWebSocketClient();
  }

  private initializeWebSocketClient(): void {
    this.client = new Client({
      brokerURL: environment.wsUrl,
      connectHeaders: {},
      debug: function (str) {
        console.log('STOMP: ' + str);
      }
    });

    this.client.onConnect = () => {
      console.log('Connected to WebSocket');
    };

    this.client.onStompError = (frame) => {
      console.error('STOMP error:', frame);
    };

    this.client.activate();
  }

  joinRoom(roomId: string): void {
    // Unsubscribe from previous room if any
    if (this.roomSubscription) {
      this.roomSubscription.unsubscribe();
    }

    this.currentRoom = roomId;

    // Subscribe to the new room
    this.roomSubscription = this.client.subscribe(`/topic/chat/${roomId}`, (message: IMessage) => {
      const chatMessage: ChatMessage = JSON.parse(message.body);
      const messages = this.messagesSubject.value;
      this.messagesSubject.next([...messages, chatMessage]);
    });

    // Load previous messages for this room
    // This would be implemented via a REST API call
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

  disconnect(): void {
    if (this.client) {
      this.client.deactivate();
    }
  }
}
