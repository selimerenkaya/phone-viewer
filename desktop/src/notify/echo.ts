/** Remembers the last clipboard text seen from either side, so a value is never bounced back where it came from. */
export class EchoGuard {
  private last: string | undefined;

  received(text: string): void {
    this.last = text;
  }

  shouldSend(text: string): boolean {
    if (text === this.last) return false;
    this.last = text;
    return true;
  }
}
