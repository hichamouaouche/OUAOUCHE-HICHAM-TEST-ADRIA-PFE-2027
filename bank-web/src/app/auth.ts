import Keycloak from 'keycloak-js';
import { HttpInterceptorFn } from '@angular/common/http';
import { from, switchMap } from 'rxjs';
import { environment } from './environment';

const keycloak = new Keycloak({
  url: environment.keycloakUrl,
  realm: 'bank',
  clientId: 'bank-web',
});

export function initAuth(): Promise<boolean> {
  return keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false });
}

export function logout(): void {
  void keycloak.logout({ redirectUri: window.location.origin });
}

export function username(): string {
  return keycloak.tokenParsed?.['preferred_username'] ?? '';
}

export function isAdmin(): boolean {
  return keycloak.hasRealmRole('ADMIN');
}

/** Ajoute le token, renouvelé s'il expire dans moins de 30 s, aux seuls appels vers la gateway. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(environment.apiUrl)) {
    return next(request);
  }
  return from(keycloak.updateToken(30)).pipe(
    switchMap(() =>
      next(request.clone({ setHeaders: { Authorization: `Bearer ${keycloak.token}` } })),
    ),
  );
};
