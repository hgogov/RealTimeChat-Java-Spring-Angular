import { Pipe, PipeTransform } from '@angular/core';

import { format, isToday, isYesterday, isThisWeek, parseISO } from 'date-fns';

@Pipe({
  name: 'relativeTime',
  standalone: true,
})
export class RelativeTimePipe implements PipeTransform {
  transform(value: string | Date | undefined | null): string {
    if (!value) {
      return '';
    }

    try {
      const date = typeof value === 'string' ? parseISO(value) : value;
      const now = new Date();

      if (isToday(date)) {
        return format(date, 'p');
      }

      if (isYesterday(date)) {
        return `Yesterday at ${format(date, 'p')}`;
      }

      if (isThisWeek(date, { weekStartsOn: 1 })) {
        return format(date, "eeee 'at' p");
      }

      return format(date, 'MMM d, yyyy');

    } catch (error) {
      console.error('RelativeTimePipe: Invalid date value', value, error);
      return 'Invalid date';
    }
  }
}
