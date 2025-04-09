import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription, StompHeaders, IFrame } from '@stomp/stompjs';
import { BehaviorSubject, Observable, ReplaySubject, Subject, Subscription, filter, first, takeUntil, firstValueFrom, timer } from 'rxjs';
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
  private client!: Client;
  private connectionRetryTimer?: Subscription;

  private destroy$ = new Subject<void>();

  // Connection state
  private connectionState$ = new ReplaySubject<boolean>(1);
  public isConnected$ = this.connectionState$.asObservable();

  // Message streams (Using Subject for single emissions)
  private messagesSubject = new Subject<ChatMessage>();
  public messages$ = this.messagesSubject.asObservable();

  private presenceSubject = new BehaviorSubject<PresenceEvent[]>([]);
  public presence$ = this.presenceSubject.asObservable();

  private typingSubject = new BehaviorSubject<TypingEvent | null>(null);
  public typing$ = this.typingSubject.asObservable();

  // Room management
  private currentRoomSubject = new BehaviorSubject<string | null>(null);
  public currentRoom$ = this.currentRoomSubject.asObservable();

  // Subscription references
  private roomMessageSubscription?: StompSubscription;
  private roomTypingSubscription?: StompSubscription;
  private presenceSubscription?: StompSubscription;
  private presenceListSubscription?: StompSubscription;

  constructor(private authService: AuthService) {
    this.initializeClient();
    this.initializeConnectionListener();
    if (this.authService.getToken()) {
      this.activateConnection();
    } else {
      this.connectionState$.next(false);
    }
  }

  private initializeClient(): void {
    console.log('[WebSocket] Initializing STOMP client...');
    this.client = new Client({
        webSocketFactory: () => {
           const token = this.authService.getToken();
           const connector = token ? `?token=${encodeURIComponent(token)}` : '';
           const sockJsUrl = `/ws${connector}`;
           console.log(`[WebSocket] SockJS attempting connection to endpoint: ${sockJsUrl}`);
           return new SockJS(sockJsUrl, null, { timeout: 15000 });
        },
        debug: (str) => console.debug('[STOMP]', str.substring(0, 150)),
        reconnectDelay: 5000, // Standard reconnect delay
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: (frame: IFrame) => this.handleConnect(frame),
        onStompError: (frame: IFrame) => this.handleError(frame),
        onWebSocketError: (event: Event | CloseEvent | any) => this.handleWebsocketError(event),
        onDisconnect: (frame: IFrame) => this.handleDisconnect(frame),
      });
  }

  private activateConnection(): void {
      if (this.client?.active) {
          console.log('[WebSocket] Client already active.');
          return;
      }
      if (!this.authService.getToken()) {
          console.warn('[WebSocket] Activation aborted: No auth token found.');
          return;
      }
      console.log('[WebSocket] Activating connection...');
      this.initializeClient();
      this.client.activate();
  }

  // Listen for login/logout events
  private initializeConnectionListener(): void {
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        if (user && this.authService.getToken()) {
            if (!this.client || !this.client.active) {
                this.activateConnection();
            }
        } else {
            if (this.client?.active) {
                this.disconnect();
            }
        }
      });
  }

  // --- STOMP Event Handlers ---

  private handleConnect(frame: IFrame): void {
    console.log('[WebSocket] Connection established. User:', frame?.headers['user-name']);
    this.connectionState$.next(true);
    this.initializePresenceTracking(); // Subscribe to presence topics

    const intendedRoom = this.currentRoomSubject.value;
    if (intendedRoom) {
      console.log(`[WebSocket] Re-joining intended room after connect: ${intendedRoom}`);
      this.joinRoom(intendedRoom); // Call joinRoom which handles subscriptions
    }
    this.connectionRetryTimer?.unsubscribe();
  }

  private handleError(frame: IFrame): void {
    const errorMessage = frame.headers?.['message'] || frame.body || 'Unknown STOMP error';
    console.error('[WebSocket] STOMP Error:', errorMessage, 'Headers:', frame.headers);
    this.connectionState$.next(false);
     if (errorMessage.includes('AccessDeniedException') || errorMessage.includes('Authentication Failed')) {
        console.warn('[WebSocket] Authentication error during STOMP communication. Consider logging out.');
    }
  }

  private handleWebsocketError(event: Event | CloseEvent | any): void {
      console.error('[WebSocket] WebSocket/SockJS transport error:', event);
      this.connectionState$.next(false);
      if (!this.client.active) {
          console.log('[WebSocket] WebSocket error on inactive client. Attempting reactivation soon via reconnectDelay...');
      }
      if (event instanceof CloseEvent) {
          console.error(`[WebSocket] Connection closed: Code=${event.code}, Reason=${event.reason}, Clean=${event.wasClean}`);
      }
  }

  private handleDisconnect(frame: IFrame): void {
    console.log('[WebSocket] Disconnected. Frame:', frame?.command);
    this.connectionState$.next(false);
    this.clearSubscriptions();
  }

  // --- Presence Handling ---

  private initializePresenceTracking(): void {
    if (!this.client.connected) {
        console.warn('[WebSocket] Cannot initialize presence tracking - client not connected.');
        return;
    }

    console.log('[WebSocket] Initializing presence subscriptions...');
    if (this.presenceSubscription) this.presenceSubscription.unsubscribe();
    if (this.presenceListSubscription) this.presenceListSubscription.unsubscribe();

    // 1. Subscribe to SINGLE presence updates
    this.presenceSubscription = this.client.subscribe(
        '/topic/presence',
        (message: IMessage) => this.processPresenceUpdate(message),
        { id: 'topic-presence-sub', ...this.getAuthHeaders() }
    );

    // 2. Subscribe to the FULL LIST broadcast topic
    this.presenceListSubscription = this.client.subscribe(
        '/topic/presence.list',
        (message: IMessage) => this.processPresenceList(message),
        { id: 'topic-presence-list-sub', ...this.getAuthHeaders() }
    );

    // 3. Request the initial list AFTER subscriptions are set up
    timer(500).pipe(takeUntil(this.destroy$)).subscribe(() => {
        this.requestInitialPresenceList();
    });
  }

  private processPresenceUpdate(message: IMessage): void {
      try {
        const update: PresenceEvent = JSON.parse(message.body);
        console.log('[WebSocket] Received presence update on /topic/presence:', update);
        if (update && typeof update === 'object' && update.username) {
          const currentUsers = [...this.presenceSubject.value];
          const index = currentUsers.findIndex(u => u.username === update.username);

          if (update.online) {
            if (index > -1) currentUsers[index] = update;
            else currentUsers.push(update);
            this.presenceSubject.next(currentUsers);
          } else {
            if (index > -1) {
              const nextUsers = currentUsers.filter(u => u.username !== update.username);
              this.presenceSubject.next(nextUsers);
            }
          }
        } else { console.warn('[WebSocket] Invalid presence update format'); }
      } catch (e) { console.error('[WebSocket] Failed to parse presence update:', e); }
  }

  private processPresenceList(message: IMessage): void {
      try {
        const fullList: PresenceEvent[] = JSON.parse(message.body);
        console.log('[WebSocket] Received presence list on /topic/presence.list:', fullList);
        if (Array.isArray(fullList)) {
           const onlineUsers = fullList.filter(u => u.online);
           console.log('[WebSocket] Emitting full online presence list:', onlineUsers);
           this.presenceSubject.next([...onlineUsers]);
        } else { console.warn('[WebSocket] Invalid presence list format'); }
      } catch (e) { console.error('[WebSocket] Failed to parse presence list:', e); }
  }

  private requestInitialPresenceList(): void {
      if (!this.client.connected) {
          console.warn('[WebSocket] Cannot request presence list, client not connected.');
          return;
      }
      console.log('[WebSocket] Sending request for initial presence list to /app/presence.requestList');
      try {
        this.client.publish({
            destination: '/app/presence.requestList',
            body: '',
            headers: this.getAuthHeaders()
        });
      } catch (e) {
          console.error('[WebSocket] Error publishing presence list request:', e);
      }
  }


  // --- Room Handling ---

  public joinRoom(roomId: string): void {
    if (!roomId) {
      console.error('[WebSocket] Cannot join room: Invalid roomId provided.');
      return;
    }

    if (!this.client?.connected) {
      console.warn(`[WebSocket] Client not connected. Setting target room to ${roomId}. Will join upon connect.`);
      this.currentRoomSubject.next(roomId);
       if (!this.client || !this.client.active) {
           this.activateConnection();
       }
      return;
    }

    console.log(`[WebSocket] Attempting to join room: ${roomId}`);
    this.leaveCurrentRoom();
    this.currentRoomSubject.next(roomId);
    this.subscribeToRoomTopics(roomId);
  }

  private subscribeToRoomTopics(roomId: string): void {
     if (!this.client.connected || !roomId) {
        console.warn('[WebSocket] Cannot subscribe to room topics - client not connected or no room ID.');
        return;
     }
     console.log(`[WebSocket] Subscribing to topics for room: ${roomId}`);

     // Ensure old subs are cleared
     if(this.roomMessageSubscription) this.roomMessageSubscription.unsubscribe();
     if(this.roomTypingSubscription) this.roomTypingSubscription.unsubscribe();

     // Subscribe to new chat messages
     const messageDestination = `/topic/chat/${roomId}`;
     this.roomMessageSubscription = this.client.subscribe(
       messageDestination,
       (message: IMessage) => {
         try {
             const chatMessage: ChatMessage = JSON.parse(message.body);
             this.messagesSubject.next(chatMessage);
         } catch (e) { console.error('[WebSocket] Failed to parse chat message:', e); }
       },
       { id: `room-${roomId}-msg-sub`, ...this.getAuthHeaders() }
     );
     console.log(`[WebSocket] Subscribed to ${messageDestination}`);

     // Subscribe to typing events
     const typingDestination = `/topic/typing/${roomId}`;
     this.roomTypingSubscription = this.client.subscribe(
       typingDestination,
       (message: IMessage) => {
          try {
             const typingEvent: TypingEvent = JSON.parse(message.body);
             const currentUser = this.authService.currentUserValue;
             if (typingEvent.username !== currentUser?.username) {
                this.typingSubject.next(typingEvent);
             }
          } catch (e) { console.error('[WebSocket] Failed to parse typing message:', e); }
       },
       { id: `room-${roomId}-typing-sub`, ...this.getAuthHeaders() }
     );
     console.log(`[WebSocket] Subscribed to ${typingDestination}`);
  }

  public leaveCurrentRoom(): void {
    const roomToLeave = this.currentRoomSubject.value;
      if (roomToLeave) {
      console.log(`[WebSocket] Leaving room: ${roomToLeave}`);
      if (this.roomMessageSubscription) {
          try { this.roomMessageSubscription.unsubscribe(); } catch(e){}
          this.roomMessageSubscription = undefined;
      }
      if (this.roomTypingSubscription) {
          try { this.roomTypingSubscription.unsubscribe(); } catch(e){}
          this.roomTypingSubscription = undefined;
      }
      this.typingSubject.next(null);
      this.currentRoomSubject.next(null);
    }
  }

  // --- Sending Actions ---

  public async sendMessage(message: Omit<ChatMessage, 'sender' | 'timestamp' | 'id' | 'roomId'>): Promise<void> {
      const currentRoomId = this.currentRoomSubject.value;
      if (!currentRoomId) {
          console.error('Cannot send message - not currently in a room.');
          throw new Error('Not in a room');
      }
      const currentUser = this.authService.currentUserValue;
       if (!currentUser?.username) {
          console.error('Cannot send message - no authenticated user.');
          throw new Error('Not authenticated');
      }

      try {
        await this.waitForConnection();
      } catch (error) {
          console.error('Cannot send message - connection failed or timed out.', error);
          throw error;
      }

      const completeMessage: ChatMessage = {
          ...message,
          roomId: currentRoomId,
          sender: currentUser.username,
          timestamp: new Date().toISOString()
      };

      console.log('[WebSocket] Publishing message:', completeMessage);
      this.client.publish({
          destination: '/app/chat.sendMessage',
          body: JSON.stringify(completeMessage),
          headers: this.getAuthHeaders()
      });
  }

  public async sendTyping(isTyping: boolean): Promise<void> {
      const currentRoomId = this.currentRoomSubject.value;
      if (!currentRoomId) return;

      const currentUser = this.authService.currentUserValue;
      if (!currentUser?.username) return;

       try {
          await this.waitForConnection(1000);
      } catch (error) {
          return;
      }

      this.client.publish({
          destination: '/app/chat.typing',
          body: JSON.stringify({
              roomId: currentRoomId,
              username: currentUser.username,
              typing: isTyping
          }),
          headers: this.getAuthHeaders()
      });
  }


  // --- Helper Methods ---

  private waitForConnection(timeoutMs = 5000): Promise<boolean | void> {
    if (this.client?.connected) {
      return Promise.resolve();
    }
    const timeoutPromise = new Promise<void>((_, reject) =>
        setTimeout(() => reject(new Error(`Connection timeout after ${timeoutMs}ms`)), timeoutMs)
    );
    const connectionPromise = firstValueFrom(
        this.isConnected$.pipe(filter(isConnected => isConnected), first())
    );

    return Promise.race([connectionPromise, timeoutPromise]).catch(error => {
          console.error('[WebSocket] Failed waiting for connection:', error);
          throw error;
    });
  }

   private getAuthHeaders(): StompHeaders {
    const token = this.authService.getToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  // --- Cleanup ---

  private clearSubscriptions(): void {
    console.log('[WebSocket] Clearing ALL local STOMP subscriptions...');
    this.leaveCurrentRoom();

    if (this.presenceSubscription) { try { this.presenceSubscription.unsubscribe(); } catch(e) {} this.presenceSubscription = undefined; }
    if (this.presenceListSubscription) { try { this.presenceListSubscription.unsubscribe(); } catch(e) {} this.presenceListSubscription = undefined; }

    this.presenceSubject.next([]);
    this.typingSubject.next(null);
    this.currentRoomSubject.next(null);
  }

  public disconnect(): void {
    console.log('[WebSocket] Disconnect called, cleaning up...');
    this.connectionRetryTimer?.unsubscribe();
    this.clearSubscriptions();
    if (this.client?.deactivate) {
      try {
        this.client.deactivate().then(() => {
             console.log('[WebSocket] Client deactivated.');
             this.connectionState$.next(false);
        });
      } catch (error) {
        console.error('[WebSocket] Error during client deactivation:', error);
         this.connectionState$.next(false);
      }
    } else {
        this.connectionState$.next(false);
    }
  }

  ngOnDestroy(): void {
    console.log('[WebSocket] Service ngOnDestroy.');
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnect();
  }
}
