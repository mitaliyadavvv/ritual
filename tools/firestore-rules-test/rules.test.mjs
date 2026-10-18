// Verifies firestore.rules against the real Firestore emulator.
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, getDocs, collection, deleteDoc } from "firebase/firestore";
import { readFileSync } from "node:fs";

const env = await initializeTestEnvironment({
  projectId: "demo-ritual",
  firestore: { rules: readFileSync(process.argv[2], "utf8"), host: "127.0.0.1", port: 8180 },
});
const habit = { name: "Read", description: null, sortOrder: 0, createdAt: 1, archivedAt: null, deletedAt: null,
  reminderEnabled: false, reminderMinutes: null, periods: [{ start: "2026-10-18", end: null }], updatedAt: 5 };
const alice = env.authenticatedContext("alice").firestore();
const bob = env.authenticatedContext("bob").firestore();
const anon = env.unauthenticatedContext().firestore();
const results = [];
async function check(name, p) { try { await p; results.push(["PASS", name]); } catch (e) { results.push(["FAIL", name, e.message]); } }

await check("owner can write own habit", assertSucceeds(setDoc(doc(alice, "users/alice/habits/h1"), habit)));
await check("owner can write own completion", assertSucceeds(setDoc(doc(alice, "users/alice/completions/h1_2026-10-18"), { habitId: "h1", date: "2026-10-18", done: true, updatedAt: 5 })));
await check("owner can write own settings", assertSucceeds(setDoc(doc(alice, "users/alice/settings/preferences"), { themeMode: "DARK", hapticsEnabled: true, updatedAt: 5 })));
await check("owner can read own habit", assertSucceeds(getDoc(doc(alice, "users/alice/habits/h1"))));
await check("owner can list own habits", assertSucceeds(getDocs(collection(alice, "users/alice/habits"))));
await check("other user cannot read", assertFails(getDoc(doc(bob, "users/alice/habits/h1"))));
await check("other user cannot list", assertFails(getDocs(collection(bob, "users/alice/habits"))));
await check("other user cannot list completions", assertFails(getDocs(collection(bob, "users/alice/completions"))));
await check("other user cannot write", assertFails(setDoc(doc(bob, "users/alice/habits/h1"), habit)));
await check("other user cannot delete", assertFails(deleteDoc(doc(bob, "users/alice/habits/h1"))));
await check("signed-out cannot read", assertFails(getDoc(doc(anon, "users/alice/habits/h1"))));
await check("signed-out cannot write", assertFails(setDoc(doc(anon, "users/alice/habits/h2"), habit)));
await check("unknown collection rejected", assertFails(setDoc(doc(alice, "users/alice/other/x"), { updatedAt: 1 })));
await check("missing updatedAt rejected", assertFails(setDoc(doc(alice, "users/alice/habits/h3"), { name: "x" })));
await check("top-level collections closed", assertFails(setDoc(doc(alice, "public/x"), { updatedAt: 1 })));
await env.cleanup();
for (const r of results) console.log(r.join(" | "));
process.exit(results.some(r => r[0] === "FAIL") ? 1 : 0);
