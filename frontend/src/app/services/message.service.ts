import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ChatMessage } from './websocket.service';

@Injectable({
  providedIn: 'root'
})
export class MessageService {
  constructor(private http: HttpClient) {}

  getMessagesByRoom(roomId: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${environment.apiUrl}/api/messages/${roomId}`);
  }

  getMessages(roomId: string, page = 0, size = 20): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${environment.apiUrl}/api/messages`, {
      params: { roomId, page: page.toString(), size: size.toString() }
    });
  }
}
