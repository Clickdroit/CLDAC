# ⚔️ CLDAC — Modular Minecraft Anti-Cheat Engine

Un système anti-triche côté serveur performant et modulaire pour **Minecraft Spigot 1.8.8+**, conçu spécifiquement pour les serveurs compétitifs et les environnements UHC.

---

## 🚀 Vue d'ensemble

**CLDAC** analyse les paquets réseau entrants et le comportement physique des joueurs en temps réel afin d'intercepter les clients modifiés et les modules de triche, tout en maintenant une charge CPU minimale sur le serveur.

```
                    +------------------------------------+
                    |       Paquets Joueur (Client)      |
                    +-----------------+------------------+
                                      |
                                      v
                    +------------------------------------+
                    |  PacketListener & BukkitListener   |
                    +-----------------+------------------+
                                      |
                                      v
                    +------------------------------------+
                    |         PlayerDataManager          |
                    | (Cache de session & Historique)    |
                    +-----------------+------------------+
                                      |
                                      v
                    +------------------------------------+
                    |          CheckManager              |
                    | (Combat, Mouvement, Joueur/Paquets)|
                    +-----------------+------------------+
                                      |
                 [ Violation détectée (> seuil VL) ]
                                      |
                                      v
                    +------------------------------------+
                    |   AlertManager & ViolationManager  |
                    |    (Alertes staff, logs, kick)     |
                    +------------------------------------+
```

---

## 🛡️ Modules de Détection (Checks)

### 🗡️ Combat
- **`KillAura`** : Analyse des vecteurs de regard, des angles de visée anormaux (snap/heuristic) et du ciblage multi-entités.
- **`Reach`** : Calcul précis de la distance œil-cible au moment du paquet de frappe avec prise en compte de la latence (ping).
- **`AutoClicker`** : Analyse statistique des CPS (moyenne, écart-type, régularité non humaine et pics de clics).
- **`Criticals`** : Vérification de la trajectoire en $Y$ et de la cohérence des paquets `onGround` lors des coups critiques.
- **`Velocity`** : Mesure de l'impact des knockbacks appliqués par le serveur vs trajectoire réelle du joueur.

### 🏃 Déplacement (Movement)
- **`Speed (A / B)`** : Contrôle des limites de friction et de vélocité théoriques au sol et dans les airs (avec gestion des effets de potions).
- **`Fly (A / B)`** : Détection des suspensions anormales en l'air, absence de gravité et modifications de hauteur.
- **`Jesus`** : Détection de marche ou de saut sur des surfaces liquides sans flottaison naturelle.
- **`NoFall`** : Détection de l'annulation fallacieuse des dégâts de chute par falsification de l'attribut `onGround`.
- **`Phase`** : Prévention de la traversée de blocs solides ou de portes fermées.

### 👤 Joueur & Environnement (Player)
- **`BadPackets`** : Détection de séquences de paquets invalides ou impossibles avec le client vanilla (ex: doubles clics de slot, ordres contradictoires).
- **`PacketRate`** : Limitation et surveillance du débit de paquets reçus (anti-flood / timer hack).
- **`NoSlowdown`** : Empêche le déplacement à vitesse normale lors de la consommation de nourriture ou de l'utilisation d'un arc.
- **`InventoryMove`** : Détecte les mouvements et sprints effectués pendant que les menus d'inventaire sont ouverts.
- **`Scaffold`** : Analyse des angles et des placements rapides de blocs en marche arrière ou sous les pieds.
- **`XRay`** : Suivi des statistiques d'extraction de minerais précieux par joueur.

---

## ⚙️ Architecture Technique

- **`PlayerData` / `PlayerDataManager`** : Structure de données en mémoire cache dédiée à chaque joueur connecté pour un accès constant sans impact I/O.
- **`ViolationManager` (VL)** : Système progressif de niveaux de violation. Chaque flag incrémente le VL du joueur ; passé un seuil configurable, une sanction automatique peut être déclenchée.
- **`AlertManager`** : Diffusion d'alertes détaillées en jeu pour le staff connecté avec permissions (nom du check, type, ping, niveau de VL).
- **`ConfigManager`** : Configuration intégrale via `config.yml` (activation des modules, seuils de kick, format des messages).

---

## 📦 Compilation & Déploiement

### Prérequis
- Java JDK 8 ou supérieur.
- Gradle.

### Compilation
```bash
./gradlew build
```
Le fichier JAR généré se trouvera dans le dossier `build/libs/`.

### Installation
1. Déposez le fichier `.jar` généré dans le dossier `/plugins/` de votre serveur Spigot / Paper 1.8.8+.
2. Redémarrez le serveur pour générer la configuration `plugins/CLDAC/config.yml`.
3. Ajustez les permissions et les seuils d'alertes selon vos besoins.
