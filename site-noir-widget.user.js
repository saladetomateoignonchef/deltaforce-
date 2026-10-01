// ==UserScript==
// @name         Delta Force - Widget Site noir
// @namespace    deltaforce-site-noir
// @version      1.0
// @description  Affiche uniquement l'Atelier du Site noir (production en cours + recommandations) avec un bouton Rafraîchir.
// @match        https://www.playdeltaforce.com/events/hq/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

(function () {
  'use strict';

  // Le mode widget ne s'active que si l'URL contient "widget" (ex : index.html?widget).
  // Comme ça, le site reste normal quand tu l'ouvres sans ce paramètre.
  if (!/[?&#]widget\b/.test(location.href)) return;

  const TEXTES = ['Détails de la production', 'Recommandations de production'];
  const TITRE = 'Atelier du Site noir';
  const DELAI_MAX_MS = 30000;

  // Barre du haut : bouton Rafraîchir, heure de mise à jour, bouton pour voir le site complet
  const barre = document.createElement('div');
  barre.id = 'sn-barre';
  barre.innerHTML =
    '<button id="sn-refresh">⟳ Rafraîchir</button>' +
    '<span id="sn-statut">Chargement…</span>' +
    '<button id="sn-complet" title="Afficher le site complet (pour se connecter)">Site</button>';

  const style = document.createElement('style');
  style.textContent = `
    #sn-barre {
      position: fixed; top: 0; left: 0; right: 0; z-index: 2147483647;
      display: flex; align-items: center; gap: 10px;
      padding: 8px 12px; background: #1b2426; color: #d8e0e0;
      font: 14px/1.2 sans-serif; border-bottom: 1px solid #3a4a4c;
    }
    #sn-barre button {
      background: #2d3b3d; color: #fff; border: 1px solid #4a5d60;
      border-radius: 6px; padding: 8px 14px; font-size: 15px;
    }
    #sn-refresh { background: #1f6f4a !important; border-color: #2a9a66 !important; }
    #sn-statut { flex: 1; text-align: center; opacity: .8; }
    html.sn-widget, html.sn-widget body { background: #0f1a1b !important; }
    html.sn-widget body { padding-top: 56px !important; }
    .sn-cache { display: none !important; }
    .sn-bloc { margin: 0 auto !important; float: none !important; position: static !important;
               transform: none !important; }
  `;
  document.head.appendChild(style);
  document.body.appendChild(barre);
  document.documentElement.classList.add('sn-widget');

  const statut = barre.querySelector('#sn-statut');
  const heure = () => new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });

  barre.querySelector('#sn-refresh').addEventListener('click', () => {
    statut.textContent = 'Rafraîchissement…';
    location.reload();
  });

  let modeComplet = false;
  barre.querySelector('#sn-complet').addEventListener('click', () => {
    modeComplet = !modeComplet;
    document.querySelectorAll('.sn-cache-off, .sn-cache').forEach((el) => {
      el.classList.toggle('sn-cache', !modeComplet);
      el.classList.toggle('sn-cache-off', modeComplet);
    });
    document.documentElement.classList.toggle('sn-widget', !modeComplet);
    barre.querySelector('#sn-complet').textContent = modeComplet ? 'Widget' : 'Site';
  });

  // Cherche le plus petit élément de la page qui contient les deux sections.
  // On se base sur le texte et pas sur les classes CSS du site : ça résiste mieux aux mises à jour du site.
  function trouverBloc() {
    let meilleur = null;
    for (const el of document.body.querySelectorAll('*')) {
      if (el === barre || barre.contains(el)) continue;
      const t = el.textContent;
      if (!TEXTES.every((x) => t.includes(x))) continue;
      if (!meilleur || t.length < meilleur.textContent.length) meilleur = el;
    }
    if (!meilleur) return null;
    // Si possible, on remonte pour inclure le titre "Atelier du Site noir" et la date
    let el = meilleur;
    while (el.parentElement && el.parentElement !== document.body && !el.textContent.includes(TITRE)) {
      el = el.parentElement;
    }
    return el.textContent.includes(TITRE) ? el : meilleur;
  }

  // Cache tout ce qui n'est pas le bloc (frères de chaque ancêtre), sans rien supprimer :
  // le site continue de fonctionner normalement en arrière-plan.
  function isoler(bloc) {
    bloc.classList.add('sn-bloc');
    let el = bloc;
    while (el && el !== document.body) {
      for (const frere of el.parentElement.children) {
        if (frere !== el && frere !== barre && frere.tagName !== 'SCRIPT' && frere.tagName !== 'STYLE') {
          frere.classList.add('sn-cache');
        }
      }
      el.classList.add('sn-bloc');
      el = el.parentElement;
    }
    // Adapte la taille à l'écran du téléphone
    const largeur = bloc.getBoundingClientRect().width;
    if (largeur > window.innerWidth) bloc.style.zoom = String(window.innerWidth / largeur);
    window.scrollTo(0, 0);
  }

  const debut = Date.now();
  (function attendre() {
    const bloc = trouverBloc();
    if (bloc) {
      isoler(bloc);
      statut.textContent = 'Mis à jour à ' + heure();
    } else if (Date.now() - debut < DELAI_MAX_MS) {
      setTimeout(attendre, 500); // les données arrivent après le chargement de la page
    } else {
      statut.textContent = 'Introuvable : connecte-toi (bouton Site)';
    }
  })();
})();
