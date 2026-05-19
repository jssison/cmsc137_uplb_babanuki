# 🎮 UPLB Babanuki

**UPLB Babanuki** is a real-time, free-for-all twist on the classic Japanese card game *Babanuki (Old Maid)*. Instead of taking turns, all players act simultaneously in a fast-paced race to form pairs, use trap cards, and avoid being left with the unpaired Queen.

---

## 🎯 Objective

Your goal is simple:

- **Get rid of all your cards by forming pairs faster than everyone else**

But there is a catch:

- The player left holding the **unpaired Queen card loses the game**

---

## 🃏 Core Gameplay

UPLB Babanuki is designed to be **real-time and chaotic**:

- There are **no turns**
- All players act at the same time
- Players constantly draw and match cards
- Speed and timing matter more than planning

### 🔄 Game Loop

1. Draw cards from the pool or other players  
2. Try to form matching pairs in your hand  
3. Matched pairs are automatically discarded  
4. Activate trap cards when available  
5. Empty your hand as quickly as possible  

---

## 👑 The Queen Rule (Losing Condition)

The Queen cards are the danger cards of the game:

- Queen of Spades ♠  
- Queen of Clubs ♣  
- Queen of Diamonds ♦  

### ⚠️ Rules:
- Queens **cannot be paired**
- They remain in your hand until the end
- If you are left with the **final unpaired Queen**, you lose

---

## 💣 Trap Cards

Trap cards introduce strategy and disruption into the game.

### ⚙️ How Trap Cards Work

- Trap cards do **not auto-discard**
- You must collect a **pair of identical trap cards**
- When paired, they become activatable (they glow in-game)
- You must manually click one card to trigger its effect

---

## ⚡ Trap Card Types

### 🔹 DOS (2)

**Effect: Burst Speed Advantage**

- Grants **3 extra actions**
- Removes cooldown for your next 3 actions
- Enables rapid drawing and pairing

**Best used for:** aggressive plays and fast hand clearing

---

### 🔹 SINGKO (5)

**Effect: Global Freeze**

- Applies a **5-second cooldown to all opponents**
- Prevents them from acting temporarily
- Gives you a window to play freely

**Best used for:** disrupting fast or leading players

---

### 🔹 AMIS (Ace)

**Effect: Hand Swap**

- Pauses the game
- Lets you choose a target player
- Swaps your entire hand with theirs

**Best used for:** high-risk, high-reward comebacks

---

## 🧠 Strategy Tips

- Avoid holding Queens for too long
- Watch opponents since there are no turns
- Use DOS to recover from bad hands quickly
- Use SINGKO to stall strong players
- Use AMIS only when it can swing the game in your favor

---

## 🎮 Game Feel

UPLB Babanuki is designed to be:

- ⚡ Fast-paced  
- 🎲 Chaotic  
- 🧠 Competitive  
- 😈 Unpredictable  

Every match becomes a race of speed, timing, and disruption.

---

## 🧱 Tech Stack

- Java
- JavaFX (UI)

---

## 🏁 Win Condition

You win if:

- You successfully discard all your cards  
- You are **not** the last player holding a Queen  

---

## 💀 Lose Condition

You lose if:

- You are the last player holding an unpaired Queen card
