import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ChatRoom {
    id: number;
    name: string;
    createdByUsername?: string;
    createdAt: string;
}

export interface CreateRoomPayload {
    name: string;
}

@Injectable({
  providedIn: 'root'
})
export class ChatRoomService {
  private apiUrl = `${environment.apiUrl}/api/rooms`;

  constructor(private http: HttpClient) {}

  getUserRooms(): Observable<ChatRoom[]> {
    return this.http.get<ChatRoom[]>(this.apiUrl);
  }

  createRoom(payload: CreateRoomPayload): Observable<ChatRoom> {
    return this.http.post<ChatRoom>(this.apiUrl, payload);
  }

  joinRoom(roomId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${roomId}/join`, {});
  }

  leaveRoom(roomId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${roomId}/leave`);
  }

  getDiscoverableRooms(): Observable<ChatRoom[]> {
    return this.http.get<ChatRoom[]>(`${this.apiUrl}/discoverable`);
  }
}
