# Emergency shortcut icon — current contract

The only offered preset is **SMS urgence**, using Streamline Ultimate Color `messages-bubble-square-typing-1` (CC BY 4.0). Original SVG and attribution are in `third_party/streamline-ultimate-color`. The launcher uses a padded adaptive bitmap; Quick Settings uses a monochrome SMS silhouette.

One activation requests the emergency SMS immediately, without a grace timer. Repeated shortcut activations are ignored while the receiver has not claimed the pending request, preventing duplicate sends. If Android still has not started handling it, explicit cancellation via the tile or optional notification can win the claim race. Older pinned shortcut tokens remain valid with the new behavior; users replace old launcher icons by removing them and pinning the SMS shortcut. No preset migration silently enables emergency recipients.
