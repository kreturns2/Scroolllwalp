# Fond Défilant

Appli Android de fond d'écran animé : n'importe quelle image défile quand tu changes de page sur le bureau.

## Obtenir l'APK sans ordinateur (via GitHub, gratuit)

1. Crée un compte sur github.com et un nouveau dépôt (bouton **New**).
2. Dans le dépôt : **Add file > Upload files**, et dépose tout le contenu de ce dossier
   (y compris le dossier caché `.github`). Valide avec **Commit changes**.
3. Onglet **Actions** : la compilation « Compiler l'APK » démarre toute seule (≈ 3 min).
4. Ouvre la compilation terminée (coche verte) > en bas, **Artifacts > FondDefilant-apk**.
5. Dézippe le fichier téléchargé, ouvre `FondDefilant.apk` et autorise l'installation.

Si le dossier `.github` n'a pas été envoyé : **Add file > Create new file**, nom
`.github/workflows/build.yml`, et colle le contenu du fichier du même nom.

## Avec Android Studio

Ouvre le dossier, puis **Build > Build APK(s)**.

## Utilisation

1. Ouvre **Fond Défilant** > **Choisir une image**.
2. **Appliquer comme fond d'écran** > confirme.
3. Règle l'amplitude et le nombre de pages.

## Selon ton téléphone

- **Nova, Lawnchair, Niagara, Smart Launcher…** : le launcher indique la page, le défilement suit exactement.
- **Samsung (One UI), Pixel, Xiaomi…** : ces launchers ne transmettent pas la page au fond d'écran.
  L'appli détecte alors tes glissements de doigt. Indique le bon nombre de pages dans l'appli
  pour que le fond reste synchronisé.
