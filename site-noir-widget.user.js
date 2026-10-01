// ==UserScript==
// @name         Delta Force - Widget Site noir
// @namespace    deltaforce-site-noir
// @version      3.0
// @description  Affiche l'Atelier du Site noir (production en cours + recommandations) dans un widget compact avec un bouton Rafraîchir.
// @match        https://www.playdeltaforce.com/events/hq/*
// @run-at       document-idle
// @grant        none
// @updateURL    https://raw.githubusercontent.com/saladetomateoignonchef/deltaforce-/claude/installation-claude-hjnsab/site-noir-widget.user.js
// @downloadURL  https://raw.githubusercontent.com/saladetomateoignonchef/deltaforce-/claude/installation-claude-hjnsab/site-noir-widget.user.js
// ==/UserScript==

(function () {
  'use strict';

  // Le mode widget ne s'active que si l'URL contient "widget" (ex : index.html?widget).
  if (!/[?&#]widget\b/.test(location.href)) return;

  const RE_TIMER = /(?<!\d)\d{1,2}:\d{2}:\d{2}(?!\d)/;
  const DELAI_MAX_MS = 20000;
  // Couleur de rareté selon la classe lv2…lv6 de la carte (vert, bleu, violet, or, rouge)
  const COULEURS = { lv2: '#5fc77a', lv3: '#4aa3e8', lv4: '#a07de0', lv5: '#e08a3c', lv6: '#e5534b' };

  const norm = (s) => (s || '').replace(/[’`´]/g, "'").replace(/\s+/g, ' ').trim();
  const heure = (d) => d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });

  // ---------- Interface ----------
  const style = document.createElement('style');
  style.textContent = `
    html.sn-on, html.sn-on body { overflow: hidden !important; }
    #sn-widget {
      position: fixed; inset: 0; z-index: 2147483647; overflow-y: auto;
      background: #11191a; color: #d8e0e0; font: 14px/1.3 system-ui, sans-serif;
      -webkit-text-size-adjust: 100%;
    }
    #sn-widget * { box-sizing: border-box; }
    #sn-barre { position: sticky; top: 0; display: flex; align-items: center; gap: 10px;
      padding: 10px 12px; background: #1b2426; border-bottom: 1px solid #34444a; }
    #sn-barre button { background: #2d3b3d; color: #fff; border: 1px solid #4a5d60;
      border-radius: 8px; padding: 9px 14px; font-size: 15px; }
    #sn-refresh { background: #1f6f4a !important; border-color: #2a9a66 !important; }
    #sn-statut { flex: 1; text-align: center; opacity: .75; font-size: 13px; }
    .sn-titre { margin: 16px 12px 8px; font-size: 15px; font-weight: 700; color: #fff; }
    .sn-grille { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; padding: 0 12px; }
    .sn-carte { background: #1e2a2c; border: 1px solid #34444a; border-radius: 10px;
      padding: 8px; display: flex; flex-direction: column; align-items: center; text-align: center; }
    .sn-atelier { font-size: 12px; opacity: .8; min-height: 2.6em; display: flex; align-items: center; }
    .sn-carte img { width: 100%; max-width: 110px; height: 64px; object-fit: contain; margin: 4px 0; }
    .sn-objet { font-size: 13px; font-weight: 600; min-height: 2.6em; }
    .sn-valeur { margin-top: 6px; font-size: 17px; font-weight: 700; color: #fff; }
    .sn-fin { font-size: 12px; opacity: .7; }
    .sn-recomp { color: #3ddc84; }
    .sn-vide { padding: 12px; opacity: .7; }
    #sn-diag { display: none; margin: 16px 12px; }
  `;
  document.head.appendChild(style);

  const widget = document.createElement('div');
  widget.id = 'sn-widget';
  widget.innerHTML = `
    <div id="sn-barre">
      <button id="sn-refresh">⟳ Rafraîchir</button>
      <span id="sn-statut">Chargement…</span>
      <button id="sn-site">Site</button>
    </div>
    <div id="sn-contenu"></div>
    <button id="sn-diag">Copier le diagnostic</button>`;
  document.body.appendChild(widget);
  document.documentElement.classList.add('sn-on');

  const statut = widget.querySelector('#sn-statut');
  const contenu = widget.querySelector('#sn-contenu');

  widget.querySelector('#sn-refresh').addEventListener('click', () => {
    statut.textContent = 'Rafraîchissement…';
    location.reload();
  });
  // Masque le widget pour voir le site complet (pour se connecter par exemple)
  const btnSite = widget.querySelector('#sn-site');
  btnSite.addEventListener('click', () => {
    const cache = widget.classList.toggle('sn-mini');
    document.documentElement.classList.toggle('sn-on', !cache);
    contenu.style.display = cache ? 'none' : '';
    widget.style.bottom = cache ? 'auto' : '';
    btnSite.textContent = cache ? 'Widget' : 'Site';
  });
  // En cas de souci : copie le HTML du bloc pour me l'envoyer
  widget.querySelector('#sn-diag').addEventListener('click', () => {
    const blocs = [...document.querySelectorAll('[data-info="manufacture-root"]')];
    const html = blocs.length ? blocs.map((b) => b.outerHTML).join('\n\n') : document.body.innerText;
    navigator.clipboard.writeText(html.slice(0, 30000)).then(
      () => alert('Diagnostic copié ! Colle-le dans la conversation avec Claude.'),
      () => alert('Impossible de copier automatiquement.')
    );
  });

  // ---------- Lecture des cartes ----------
  // Sur mobile, le site n'a qu'une liste de cartes qu'il remplace quand on change d'onglet
  // (data-section="personal" ou "recommend"). On mémorise donc chaque section dès qu'on la voit.
  const memo = { personal: new Map(), recommend: new Map() };

  const texte = (carte, sel) => norm((carte.querySelector(sel) || {}).textContent);
  const visible = (el) => !!el && getComputedStyle(el).display !== 'none';

  function lireCartes() {
    for (const carte of document.querySelectorAll('[data-info="manufacture-card"]')) {
      const section = memo[carte.dataset.section];
      if (!section) continue;
      const atelier = texte(carte, '.typename');
      const img = carte.querySelector('[data-info="manufacture-card-image"]');
      const imgWrap = carte.querySelector('[data-info="manufacture-card-image-wrap"]');
      const pied = carte.querySelector('[data-info="manufacture-card-footer"]');
      let etat = '';
      if (visible(carte.querySelector('[data-info="manufacture-card-locked"]'))) etat = 'Verrouillé';
      else if (visible(carte.querySelector('[data-info="manufacture-card-upgrade"]'))) etat = 'En amélioration';
      else if (visible(carte.querySelector('[data-info="manufacture-card-empty"]'))) etat = 'Rien en cours';
      const lv = [...carte.classList].find((c) => /^lv\d$/.test(c));
      // La clé (id de l'établi) évite les doublons entre la version PC et la version mobile
      section.set(carte.dataset.workbenchId || atelier, {
        atelier,
        etat,
        objet: texte(carte, '[data-info="manufacture-card-name"]'),
        image: img && (!imgWrap || visible(imgWrap)) ? img.currentSrc || img.src : '',
        couleur: COULEURS[lv] || '',
        pied: pied ? norm(pied.textContent) : '',
        termine: !!pied && pied.classList.contains('collected'),
      });
    }
  }

  function cliquerOnglet(nom) {
    const onglet = document.querySelector(`[data-action="m-manufacture-tab"][data-tab="${nom}"]`);
    if (onglet) onglet.click();
  }

  // ---------- Affichage ----------
  function fin(timer) {
    const [h, m, s] = timer.split(':').map(Number);
    return heure(new Date(Date.now() + ((h * 60 + m) * 60 + s) * 1000));
  }

  function carteHTML(c, type) {
    const div = document.createElement('div');
    div.className = 'sn-carte';
    div.innerHTML = `
      <div class="sn-atelier"></div>
      ${c.image ? '<img alt="">' : ''}
      <div class="sn-objet"></div>
      <div class="sn-valeur"></div>
      <div class="sn-fin"></div>`;
    div.querySelector('.sn-atelier').textContent = c.atelier;
    if (c.image) div.querySelector('img').src = c.image;
    const objet = div.querySelector('.sn-objet');
    objet.textContent = c.objet || c.etat || '—';
    if (c.couleur) objet.style.color = c.couleur;

    const valeur = div.querySelector('.sn-valeur');
    const sous = div.querySelector('.sn-fin');
    if (type === 'personal') {
      const timer = (c.pied.match(RE_TIMER) || [])[0];
      if (timer) {
        valeur.textContent = timer;
        sous.textContent = 'fini vers ' + fin(timer);
      } else {
        valeur.textContent = c.termine ? 'Terminé ✓' : c.pied || c.etat || '—';
      }
    } else {
      // Le pied contient "Récompenses/h" puis le nombre : on garde le dernier nombre
      const nombres = c.pied.match(/\d[\d.,\s]*\d|\d/g);
      valeur.className += ' sn-recomp';
      valeur.textContent = nombres ? nombres[nombres.length - 1].trim() : c.pied || '—';
      sous.textContent = 'récompenses/h';
    }
    return div;
  }

  function section(titre, cartes, type) {
    const h = document.createElement('div');
    h.className = 'sn-titre';
    h.textContent = titre;
    contenu.appendChild(h);
    const grille = document.createElement('div');
    grille.className = cartes.size ? 'sn-grille' : 'sn-vide';
    if (cartes.size) cartes.forEach((c) => grille.appendChild(carteHTML(c, type)));
    else grille.textContent = 'Aucune donnée trouvée.';
    contenu.appendChild(grille);
  }

  // ---------- Déroulement ----------
  // 1. on attend les cartes "production" ; 2. on clique sur l'onglet Recommandations ;
  // 3. on attend les cartes "recommandations" ; 4. on affiche tout et on remet l'onglet d'origine.
  const debut = Date.now();
  let dernierClic = 0;
  (function attendre() {
    lireCartes();
    const prod = memo.personal.size;
    const reco = memo.recommend.size;
    const ecoule = Date.now() - debut;

    if (prod && !reco && Date.now() - dernierClic > 3000) {
      dernierClic = Date.now();
      cliquerOnglet('recommend');
    }
    if (!(prod && reco) && ecoule < DELAI_MAX_MS) {
      setTimeout(attendre, 400); // les données arrivent après le chargement de la page
      return;
    }
    if (dernierClic) cliquerOnglet('personal');

    contenu.innerHTML = '';
    if (!prod && !reco) {
      statut.textContent = 'Introuvable : connecte-toi (bouton Site)';
      widget.querySelector('#sn-diag').style.display = 'block';
      return;
    }
    section('Production en cours', memo.personal, 'personal');
    section('Recommandations de production', memo.recommend, 'recommend');
    statut.textContent = 'Mis à jour à ' + heure(new Date());
    if (!prod || !reco) widget.querySelector('#sn-diag').style.display = 'block';
  })();
})();
