import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription, StompHeaders, IFrame } from '@stomp/stompjs';
import { BehaviorSubject, Observable, ReplaySubject, Subject, filter, first, takeUntil, firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import SockJS from 'sockjs-client';

export interface ChatMessage {
  id?: number;
  content: string;
  sender: string;
  roomId: string;
  timestamp?: string;
}

export interface TypingEvent {
  roomId: string;
  username: string;
  typing: boolean;
}

export interface PresenceEvent {
  username: string;
  online: boolean;
}


@Injectable({
  providedIn: 'root'
})
export class WebsocketService implements OnDestroy {
  private client: Client;
  private destroy$ = new Subject<void>();

  // Connection state
  private connectionState$ = new ReplaySubject<boolean>(1);
  public isConnected$ = this.connectionState$.asObservable();

  // Message streams
  private messagesSubject = new Subject<ChatMessage>();
  public messages$ = this.messagesSubject.asObservable();

  private presenceSubject = new BehaviorSubject<PresenceEvent[]>([]);
  public presence$ = this.presenceSubject.asObservable();

  private typingSubject = new BehaviorSubject<TypingEvent | null>(null);
  public typing$ = this.typingSubject.asObservable();

  // Room management
  private currentRoom: string | null = null;
  private roomSubscription?: StompSubscription;
  private typingSubscription?: StompSubscription;
  private presenceSubscription?: StompSubscription;

  private presenceListSubscription?: StompSubscription;

  constructor(private authService: AuthService) {
    this.client = this.createStompClient();
    this.initializeConnection();
    if (this.authService.getToken()) {
        console.log('[WebSocket] Initial activation attempt on service load.');
        this.client.activate();
    } else {
        console.log('[WebSocket] No initial token, client remains inactive on load.');
        this.connectionState$.next(false);
    }
  }

  private createStompClient(): Client {
      console.log('[WebSocket] Creating STOMP client with SockJS support...');
      return new Client({
        webSocketFactory: () => {
           const token = this.authService.getToken();
           const connector = token ? `?token=${token}` : '';
           const sockJsUrl = `/ws${connector}`; // Use relative path + token param
           console.log(`[WebSocket] SockJS attempting connection to endpoint: ${sockJsUrl}`);
           // Note: Don't log the full URL with token in production
           return new SockJS(sockJsUrl);
        },

        debug: (str) => console.debug('[STOMP]', str.length > 150 ? str.substring(0, 150) + '...' : str),
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: (frame: IFrame) => this.handleConnect(frame),
        onStompError: (frame: IFrame) => this.handleError(frame),
        onWebSocketError: (event: Event | any) => this.handleWebsocketError(event),
        onDisconnect: (frame: IFrame) => this.handleDisconnect(frame)
      });
    }

  private getAuthHeaders(): StompHeaders {
    const token = this.authService.getToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  private initializeConnection(): void {
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        if (user && this.authService.getToken()) {
            if (!this.client.active) {
                console.log('[WebSocket] User detected, activating connection...');
                this.client = this.createStompClient();
                this.client.activate();
            }
        } else {
            if (this.client.active) {
                console.log('[WebSocket] No user/token, deactivating connection...');
                this.disconnect();
            }
        }
      });
  }

  private handleConnect(frame: IFrame): void {
    console.log('[WebSocket] Connection established. Frame command:', frame.command);
    this.connectionState$.next(true);
    this.initializePresenceTracking();
    if (this.currentRoom) {
      console.log(`[WebSocket] Re-joining room after connect: ${this.currentRoom}`);
      this.subscribeToRoomTopics(this.currentRoom);
    }
  }

  private handleError(frame: IFrame): void {
    const errorMessage = frame.headers?.['message'] || frame.body || 'Unknown STOMP error';
    console.error('[WebSocket] STOMP Error:', errorMessage, 'Headers:', frame.headers);
    this.connectionState$.next(false);
     if (errorMessage.includes('AccessDeniedException') || errorMessage.includes('Authentication Failed')) {
        console.warn('[WebSocket] Authentication error during STOMP communication. Logging out.');
        this.authService.logout();
    }
  }

  private handleWebsocketError(event: Event | any): void {
      console.error('[WebSocket] WebSocket/SockJS transport error:', event);
      this.connectionState$.next(false);
  }

  private handleDisconnect(frame: IFrame): void {
    console.log('[WebSocket] Disconnected. Frame command:', frame.command);
    this.connectionState$.next(false);
    this.clearSubscriptions();
  }

  private initializePresenceTracking(): void {
    if (!this.client.connected) return;

    // Unsubscribe from previous subscriptions if they exist
    if (this.presenceSubscription) {
      this.presenceSubscription.unsubscribe();
      console.log('[WebSocket] Unsubscribed from previous presence topic.');
    }

    if (this.presenceListSubscription){
      this.presenceListSubscription.unsubscribe();
    }

    // 1. Subscribe to the general presence update topic (/topic/presence)
    console.log('[WebSocket] Subscribing to general presence topic...');
    this.presenceSubscription = this.client.subscribe(
      '/topic/presence',
      (message: IMessage) => {
        try {
          const update: PresenceEvent = JSON.parse(message.body);
          console.log('[WebSocket] Received presence update:', update);
          if (update && typeof update === 'object' && update.username) {
            const currentUsers = this.presenceSubject.value;
            const index = currentUsers.findIndex(u => u.username === update.username);
            if (index > -1) {
              // Update existing user status
              currentUsers[index] = { ...currentUsers[index], online: update.online };
            } else {
              // Add new user only if they are online (handle connect events)
              if (update.online) {
                 currentUsers.push(update);
              }
            }
            // Filter out users marked as offline (handle disconnect events)
            const filteredUsers = currentUsers.filter(u => u.online);
            this.presenceSubject.next([...filteredUsers]);
          } else {
             console.warn('[WebSocket] Received invalid presence update format:', message.body);
          }
        } catch (e) {
          console.error('[WebSocket] Failed to parse presence message:', message.body, e);
        }
      },
      { id: 'presence-sub', ...this.getAuthHeaders() }
    );

    // 2. Subscribe to the user-specific queue for the initial list
    console.log('[WebSocket] Subscribing to user-specific presence list queue...');
    this.client.subscribe(
      '/topic/presence.list',
      (message: IMessage) => {
      console.log('[WebSocket] Received message on /topic/presence.list:', message.body);
      try {
        const fullList: PresenceEvent[] = JSON.parse(message.body);
        console.log('[WebSocket] Parsed presence list from topic:', fullList);
        if (Array.isArray(fullList)) {
           const onlineUsers = fullList.filter(u => u.online);
           console.log('[WebSocket] Emitting full presence list from topic:', onlineUsers);
           this.presenceSubject.next([...onlineUsers]); // Update subject with the full list
        } else {
            console.warn('[WebSocket] Received invalid presence list format on topic:', message.body);
        }
      } catch (e) {
        console.error('[WebSocket] Failed to parse presence list from topic:', message.body, e);
      }
    },
    { id: 'topic-presence-list-sub', ...this.getAuthHeaders() }
  );
    // 3. After subscribing, REQUEST the initial list
    this.requestInitialPresenceList();
  }

  private requestInitialPresenceList(): void {
      if (!this.client.connected) {
          console.warn('[WebSocket] Cannot request presence list, client not connected.');
          return;
      }
      console.log('[WebSocket] Sending request for initial presence list to /app/presence.requestList');
      this.client.publish({
          destination: '/app/presence.requestList',
          body: '',
          headers: this.getAuthHeaders()
      });
  }

  public joinRoom(roomId: string): void {
     if (!this.client.connected) {
        if (this.client.active) {
            console.warn(`[WebSocket] Client activating, setting target room to ${roomId}. Will join upon connect.`);
            this.currentRoom = roomId;
        } else {
            console.warn('[WebSocket] Cannot join room - Client inactive. Try activating first.');
        }
      return;
    }

    console.log(`[WebSocket] Joining room: ${roomId}`);
    this.leaveCurrentRoom();
    this.currentRoom = roomId;

    this.subscribeToRoomTopics(roomId);
  }

  private subscribeToRoomTopics(roomId: string): void {
     if (!this.client.connected || !roomId) {
        console.warn('[WebSocket] Cannot subscribe to room topics - client not connected or no room ID.');
        return;
     }
     console.log(`[WebSocket] Subscribing to topics for room: ${roomId}`);

     if(this.roomSubscription) this.roomSubscription.unsubscribe();
     if(this.typingSubscription) this.typingSubscription.unsubscribe();

     this.roomSubscription = this.client.subscribe(
       `/topic/chat/${roomId}`,
        (message: IMessage) => {
          try {
              const chatMessage: ChatMessage = JSON.parse(message.body);
              this.messagesSubject.next(chatMessage);
          } catch (e) {
               console.error('[WebSocket] Failed to parse chat message:', message.body, e);
          }
        },
      { id: `room-${roomId}-msg-sub`, ...this.getAuthHeaders() }
     );

     this.typingSubscription = this.client.subscribe(
       `/topic/typing/${roomId}`,
       (message: IMessage) => {
          try {
             const typingEvent: TypingEvent = JSON.parse(message.body);
             this.typingSubject.next(typingEvent);
          } catch (e) {
               console.error('[WebSocket] Failed to parse typing message:', message.body, e);
          }
       },
       { id: `typing-${roomId}-sub`, ...this.getAuthHeaders() }
     );
  }

  private leaveCurrentRoom(): void {
    if (this.roomSubscription) {
      console.log(`[WebSocket] Unsubscribing from room messages: ${this.currentRoom}`);
      this.roomSubscription.unsubscribe();
      this.roomSubscription = undefined;
    }
    if (this.typingSubscription) {
      console.log(`[WebSocket] Unsubscribing from room typing: ${this.currentRoom}`);
      this.typingSubscription.unsubscribe();
      this.typingSubscription = undefined;
    }
     this.typingSubject.next(null);
  }

  public getPresenceUpdates(): Observable<PresenceEvent[]> {
    return this.presenceSubject.asObservable();
  }

  public getTypingUpdates(): Observable<TypingEvent | null> {
    return this.typingSubject.asObservable();
  }

  public async sendMessage(message: Omit<ChatMessage, 'sender' | 'timestamp' | 'id'>): Promise<void> {
     try {
        await this.waitForConnection();
    } catch (error) {
        console.error('Cannot send message - connection failed or timed out.', error);
        return;
    }

    const currentUser = this.authService.currentUserValue;
    if (!currentUser?.username) {
      console.error('Cannot send message - no authenticated user with username');
      return;
    }
     if (!this.currentRoom) {
        console.error('Cannot send message - not joined to a room.');
        return;
    }

    const completeMessage: ChatMessage = {
      ...message,
      roomId: this.currentRoom,
      sender: currentUser.username,
      timestamp: new Date().toISOString()
    };

    this.client.publish({
      destination: '/app/chat.sendMessage',
      body: JSON.stringify(completeMessage),
      headers: this.getAuthHeaders()
    });
  }

  public async sendTyping(isTyping: boolean): Promise<void> {
    if (!this.currentRoom) {
        return;
    }
    const currentUser = this.authService.currentUserValue;
    if (!currentUser?.username) {
        return;
    }

     try {
        await this.waitForConnection();
    } catch (error) {
        console.error('Cannot send typing status - connection failed or timed out.', error);
        return;
    }

    this.client.publish({
      destination: '/app/chat.typing',
      body: JSON.stringify({
        roomId: this.currentRoom,
        username: currentUser.username,
        typing: isTyping
      }),
      headers: this.getAuthHeaders()
    });
  }

  private waitForConnection(timeoutMs = 10000): Promise<void> {
    if (this.client.connected) {
      return Promise.resolve();
    }

    console.log('[WebSocket] Waiting for connection...');
    const timeoutPromise = new Promise<void>((_, reject) =>
        setTimeout(() => reject(new Error(`Connection timeout after ${timeoutMs}ms`)), timeoutMs)
    );

    const connectionPromise = firstValueFrom(
        this.isConnected$.pipe(
            filter(isConnected => isConnected),
            first()
        )
    ).then(() => {
         console.log('[WebSocket] Connection ready.');
    });

    return Promise.race([connectionPromise, timeoutPromise]).catch(error => {
        console.error('[WebSocket] Failed waiting for connection:', error);
        throw error;
    });
  }


  private clearSubscriptions(): void {
    console.log('[WebSocket] Clearing local STOMP subscriptions...');
    this.leaveCurrentRoom();

    if (this.presenceSubscription) {
      try { this.presenceSubscription.unsubscribe(); } catch(e) { /* ignore */ }
      this.presenceSubscription = undefined;
    }
     if (this.presenceListSubscription) { // Unsubscribe from the list topic
      try { this.presenceListSubscription.unsubscribe(); } catch(e) { /* ignore */ }
      this.presenceListSubscription = undefined;
    }

    this.presenceSubject.next([]);
    this.typingSubject.next(null);;
  }

  public disconnect(): void {
    console.log('[WebSocket] Disconnect called, cleaning up...');
    this.clearSubscriptions();
    if (this.client?.deactivate) {
      try {
        this.client.deactivate();
        console.log('[WebSocket] Client deactivated.');
      } catch (error) {
        console.error('[WebSocket] Error during client deactivation:', error);
      }
    }
    this.connectionState$.next(false);
    this.currentRoom = null;
  }

  ngOnDestroy(): void {
    console.log('[WebSocket] Service ngOnDestroy.');
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnect();
  }
}
