
# 🌄 Fond Défilant

<img width="720" height="1398" alt="Screenshot_20261102_225421_Fond Dfilant" src="https://github.com/user-attachments/assets/1a77b779-69cf-4e16-b8ca-67ebb1ea4a0a" />

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



# 🌄 Scrolling Wallpaper (Fond Défilant)

🇫🇷 [Version française](README.md)

**Turn any image into a scrolling wallpaper on your Android home screen.**

Fond Défilant is a lightweight live wallpaper: pick a photo, and it slides smoothly
as you swipe between home screen pages, just like the classic "parallax" wallpapers.

## ✨ Features

- 🖼️ **Any image**: photo, screenshot, panorama…
- 🎚️ **Adjustable scroll amount**: from 10% to 150%
- 📱 **Works with every launcher**
  - Nova, Lawnchair, Niagara…: follows the exact page position
  - Samsung, Pixel, Xiaomi…: detects your finger swipes
- 🔢 **Adjustable page count** to stay in sync with your home screen
- 🪶 **Ultra light**: no ads, no permissions, no internet access
- 🔋 **Battery friendly**: only redraws when the screen moves

## 📥 Installation

1. Open the **Actions** tab and select the latest successful build ✅
2. Download **FondDefilant-apk** at the bottom of the page
3. Unzip it and install `FondDefilant.apk` (allow unknown sources if asked)

## 🚀 Usage

1. Open **Fond Défilant**
2. Tap **Choisir une image** (Choose an image)
3. Tap **Appliquer comme fond d'écran** (Set as wallpaper) and confirm
4. Adjust the scroll amount and detection mode for your phone

> The app interface is currently in French.

## 🛠️ Tech

- Java, no external libraries
- Android 7.0+ (API 24), targets Android 15 (API 35)
- Automatic build with GitHub Actions on every change

## 📂 Structure

    app/src/main/java/com/fonddefilant/app/
    ├── ScrollWallpaperService.java   # Live wallpaper engine
    └── MainActivity.java             # Settings screen
