import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';
import { initAuth } from './app/auth';

initAuth()
  .then(() => bootstrapApplication(App, appConfig))
  .catch((err) => {
    console.error(err);
    document.body.textContent = "Connexion à Keycloak impossible. Vérifiez que le service est démarré.";
  });
