import { test, before, after } from "node:test";
import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, deleteDoc, collection, getDocs } from "firebase/firestore";

let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: "demo-meusgastos",
    firestore: { rules: readFileSync("firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 } });
});
after(async () => { await env.cleanup(); });
const db = (uid, email) => env.authenticatedContext(uid, { email, email_verified: true }).firestore();

test("fluxo completo de permissões", async () => {
  const dono = db("dono", "sergio@example.com");
  const ana = db("ana", "ana@example.com");
  const intruso = db("x", "intruso@example.com");
  const anon = env.unauthenticatedContext().firestore();

  await assertFails(getDoc(doc(anon, "casa/principal")));
  // primeiro login cria a base como dono
  await assertFails(setDoc(doc(ana, "casa/principal"), { donoUid: "dono" }));
  await assertSucceeds(setDoc(doc(dono, "casa/principal"), { donoUid: "dono", donoNome: "Sergio" }));
  // ninguém toma a base depois
  await assertFails(setDoc(doc(intruso, "casa/principal"), { donoUid: "x" }));
  await assertFails(setDoc(doc(ana, "casa/principal"), { donoUid: "ana" }));
  // dono grava dados
  await assertSucceeds(setDoc(doc(dono, "casa/principal/dados/config"), { versao: 1 }));
  await assertSucceeds(setDoc(doc(dono, "casa/principal/tx/t1"), { valor: 10 }));
  // Ana ainda não liberada
  await assertFails(getDoc(doc(ana, "casa/principal/dados/config")));
  await assertFails(setDoc(doc(ana, "casa/principal/tx/t2"), { valor: 5 }));
  // dono libera Ana; Ana não pode liberar outros
  await assertSucceeds(setDoc(doc(dono, "membros/ana@example.com"), { ok: true }));
  await assertFails(setDoc(doc(ana, "membros/intruso@example.com"), { ok: true }));
  // Ana lê e grava
  await assertSucceeds(getDocs(collection(ana, "casa/principal/tx")));
  await assertSucceeds(setDoc(doc(ana, "casa/principal/tx/t2"), { valor: 5 }));
  await assertSucceeds(deleteDoc(doc(ana, "casa/principal/tx/t2")));
  await assertSucceeds(getDocs(collection(ana, "membros")));
  // intruso continua sem acesso
  await assertFails(getDocs(collection(intruso, "casa/principal/tx")));
  await assertFails(getDocs(collection(intruso, "membros")));
  // e-mail não verificado não entra
  await assertFails(getDocs(collection(env.authenticatedContext("ana2", { email: "ana@example.com", email_verified: false }).firestore(), "casa/principal/tx")));
  // dono remove Ana
  await assertSucceeds(deleteDoc(doc(dono, "membros/ana@example.com")));
  await assertFails(getDocs(collection(ana, "casa/principal/tx")));
  // ninguém apaga a base
  await assertFails(deleteDoc(doc(dono, "casa/principal")));
});
