# 🌄 Fond Défilant

**Rends n'importe quelle image défilante sur le bureau de ton Android.**

Fond Défilant est un fond d'écran animé léger : tu choisis une photo, et elle glisse
en douceur quand tu passes d'une page à l'autre sur l'écran d'accueil, comme les
fonds d'écran « parallaxe » d'autrefois.

## ✨ Fonctionnalités

- 🖼️ **N'importe quelle image** : photo, capture, panorama…
- 🎚️ **Amplitude réglable** : de 10 % à 150 % de défilement
- 📱 **Compatible avec tous les launchers**
  - Nova, Lawnchair, Niagara… : suit exactement la position des pages
  - Samsung, Pixel, Xiaomi… : détecte tes glissements de doigt
- 🔢 **Nombre de pages réglable** pour rester synchronisé avec ton bureau
- 🪶 **Ultra léger** : aucune pub, aucune permission, aucune connexion Internet
- 🔋 **Économe** : ne dessine que quand l'écran bouge

## 📥 Installation

1. Va dans l'onglet **Actions**, puis ouvre la dernière compilation réussie ✅
2. Télécharge **FondDefilant-apk** en bas de la page
3. Dézippe le fichier et installe `FondDefilant.apk` (autorise les sources inconnues si demandé)

## 🚀 Utilisation

1. Ouvre **Fond Défilant**
2. Appuie sur **Choisir une image**
3. Appuie sur **Appliquer comme fond d'écran**, puis confirme
4. Ajuste l'amplitude et le mode de détection selon ton téléphone

## 🛠️ Technique

- Java, sans aucune bibliothèque externe
- Android 7.0 minimum (API 24), cible Android 15 (API 35)
- Compilation automatique via GitHub Actions à chaque modification

## 📂 Structure

    app/src/main/java/com/fonddefilant/app/
    ├── ScrollWallpaperService.java   # Le moteur du fond d'écran animé
    └── MainActivity.java             # L'écran de réglages
