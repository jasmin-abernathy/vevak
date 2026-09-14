# Emergency shortcut icons

The six runtime VectorDrawables now use **Streamline Ultimate Color**, explicitly selected by Jasmin on 2026-09-14. This supersedes both Papirus and the original flat VeVak prototypes, and the proposed OpenMoji preview.

Sources and attribution: [Streamline](https://www.streamlinehq.com/), CC BY 4.0. See `third_party/streamline-ultimate-color/NOTICE.md` and the original SVGs. Attribution also appears next to the icon selector in onboarding and Safety.

Run `python3 scripts/build-shortcut-vectors.py` to reproduce the vectors offline. Paths, fills and strokes are retained; a transparent 4-unit margin surrounds the 24-unit artwork in a 32-unit viewport. No external runtime dependency or network request is added.

These are alternate appearances for one emergency shortcut, not separate apps. The launcher may add its own app badge. The main VeVak icon remains unchanged. On-device mask, scale and badge rendering still need testing; existing pinned shortcuts may require recreation or a launcher refresh.

One tap arms; another cancels. A quick double tap does not confirm sending. This explanation appears in both onboarding and Safety.
