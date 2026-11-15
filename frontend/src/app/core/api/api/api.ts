export * from './auth.service';
import { AuthService } from './auth.service';
export * from './games.service';
import { GamesService } from './games.service';
export * from './invitations.service';
import { InvitationsService } from './invitations.service';
export * from './players.service';
import { PlayersService } from './players.service';
export const APIS = [AuthService, GamesService, InvitationsService, PlayersService];
