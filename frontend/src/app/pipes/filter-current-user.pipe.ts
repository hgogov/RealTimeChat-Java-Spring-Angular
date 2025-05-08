import { Pipe, PipeTransform } from '@angular/core';
import { PresenceEvent } from '../services/websocket.service';

@Pipe({
  name: 'filterCurrentUser',
  standalone: true,
})
export class FilterCurrentUserPipe implements PipeTransform {
  transform(users: PresenceEvent[] | null, currentUsername: string | null | undefined): PresenceEvent[] | null {
    if (!users) {
      return null;
    }
    if (!currentUsername) {
      return users;
    }
    return users.filter(user => user.username !== currentUsername);
  }
}
