// ==UserScript==
// @name         Delta Force - Widget Site noir
// @namespace    deltaforce-site-noir
// @version      2.0
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

  // Ordre important : "Établi d'armure" doit être testé avant "Établi"
  const ATELIERS = ["Div. de la cyberguerre", "Établi d'armure", 'Établi', 'Pharmacie'];
  const RE_TIMER = /(?<!\d)\d{1,2}:\d{2}:\d{2}(?!\d)/;
  const RE_RECOMP = /Récompenses\s*\/\s*h/i;
  const DELAI_MAX_MS = 30000;

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
    const bloc = trouverBloc();
    const html = bloc ? bloc.outerHTML : document.body.innerText;
    navigator.clipboard.writeText(html.slice(0, 30000)).then(
      () => alert('Diagnostic copié ! Colle-le dans la conversation avec Claude.'),
      () => alert("Impossible de copier automatiquement.")
    );
  });

  // ---------- Lecture des infos dans la page ----------
  // On lit textContent : il contient aussi le texte des parties cachées (onglets, carrousel…)
  function trouverBloc() {
    let meilleur = null;
    for (const el of document.body.querySelectorAll('*')) {
      if (widget.contains(el)) continue;
      const t = el.textContent;
      if (!t.includes('Atelier du Site noir') && !t.includes('Détails de la production')) continue;
      if (!RE_TIMER.test(t) && !RE_RECOMP.test(t)) continue;
      if (!meilleur || t.length < meilleur.textContent.length) meilleur = el;
    }
    return meilleur;
  }

  // Étiquettes d'atelier = éléments dont le texte est exactement un nom d'atelier
  function etiquettes(racine) {
    const res = [];
    for (const el of racine.querySelectorAll('*')) {
      const t = norm(el.textContent);
      const nom = ATELIERS.find((a) => t === a);
      if (nom && ![...el.children].some((c) => norm(c.textContent) === t)) res.push({ el, nom });
    }
    return res;
  }

  function lireCarte(carte, nom) {
    const texte = norm(carte.textContent);
    const timer = (texte.match(RE_TIMER) || [])[0];
    const recomp = RE_RECOMP.test(texte)
      ? (texte.split(RE_RECOMP)[1] || '').match(/[\d][\d.,\s]*/)
      : null;

    // Nom de l'objet : les bouts de texte qui ne sont ni l'atelier, ni le temps, ni la récompense
    let objet = '', couleur = '';
    for (const el of carte.querySelectorAll('*')) {
      if (el.children.length) continue;
      const t = norm(el.textContent);
      if (!t || t === nom || RE_TIMER.test(t) || RE_RECOMP.test(t) || /^[\d.,\s]+$/.test(t)) continue;
      if (t.length > objet.length) { objet = t; couleur = getComputedStyle(el).color; }
    }

    // Image : la plus grande de la carte (l'objet), sinon une image de fond
    let image = '';
    let taille = 0;
    for (const img of carte.querySelectorAll('img')) {
      const s = (img.naturalWidth || img.width || 1) * (img.naturalHeight || img.height || 1);
      if (img.currentSrc || img.src) if (s > taille) { taille = s; image = img.currentSrc || img.src; }
    }
    if (!image) {
      for (const el of carte.querySelectorAll('*')) {
        const m = getComputedStyle(el).backgroundImage.match(/url\(["']?(.+?)["']?\)/);
        if (m) { image = m[1]; }
      }
    }
    return {
      nom, objet, couleur, image,
      type: timer ? 'prod' : recomp ? 'reco' : null,
      valeur: timer || (recomp ? recomp[0].trim() : ''),
    };
  }

  function extraire() {
    const bloc = trouverBloc();
    if (!bloc) return null;
    const vus = new Set();
    const cartes = [];
    for (const { el, nom } of etiquettes(bloc)) {
      // On remonte jusqu'à l'élément qui contient aussi le temps ou la récompense
      let carte = el;
      while (carte !== bloc && !RE_TIMER.test(carte.textContent) && !RE_RECOMP.test(carte.textContent)) {
        carte = carte.parentElement;
      }
      if (carte === bloc) continue;
      // Si la "carte" contient plusieurs ateliers, ce n'est pas une carte : on ignore
      if (etiquettes(carte).length > 1) continue;
      const info = lireCarte(carte, nom);
      const cle = info.type + '|' + nom;
      if (!info.type || vus.has(cle)) continue; // supprime les doublons (version PC + mobile)
      vus.add(cle);
      cartes.push(info);
    }
    return { bloc, cartes };
  }

  // ---------- Affichage ----------
  function fin(timer) {
    const [h, m, s] = timer.split(':').map(Number);
    return heure(new Date(Date.now() + ((h * 60 + m) * 60 + s) * 1000));
  }

  function carteHTML(c) {
    const div = document.createElement('div');
    div.className = 'sn-carte';
    div.innerHTML = `
      <div class="sn-atelier"></div>
      ${c.image ? '<img alt="">' : ''}
      <div class="sn-objet"></div>
      <div class="sn-valeur"></div>
      ${c.type === 'prod' ? '<div class="sn-fin"></div>' : ''}`;
    div.querySelector('.sn-atelier').textContent = c.nom;
    if (c.image) div.querySelector('img').src = c.image;
    const objet = div.querySelector('.sn-objet');
    objet.textContent = c.objet || '—';
    if (c.couleur) objet.style.color = c.couleur;
    const valeur = div.querySelector('.sn-valeur');
    if (c.type === 'prod') {
      valeur.textContent = c.valeur;
      div.querySelector('.sn-fin').textContent = 'fini vers ' + fin(c.valeur);
    } else {
      valeur.innerHTML = '<span class="sn-recomp"></span><div class="sn-fin">récompenses/h</div>';
      valeur.querySelector('.sn-recomp').textContent = c.valeur;
    }
    return div;
  }

  function section(titre, cartes) {
    const h = document.createElement('div');
    h.className = 'sn-titre';
    h.textContent = titre;
    contenu.appendChild(h);
    const grille = document.createElement('div');
    grille.className = cartes.length ? 'sn-grille' : 'sn-vide';
    if (cartes.length) {
      const ordre = (c) => ["Div. de la cyberguerre", 'Établi', 'Pharmacie', "Établi d'armure"].indexOf(c.nom);
      cartes.sort((a, b) => ordre(a) - ordre(b)).forEach((c) => grille.appendChild(carteHTML(c)));
    } else {
      grille.textContent = 'Aucune donnée trouvée.';
    }
    contenu.appendChild(grille);
  }

  // Si les recommandations sont dans un onglet pas encore chargé, on clique dessus
  let ongletClique = false;
  function cliquerOngletReco() {
    if (ongletClique) return;
    ongletClique = true;
    for (const el of document.body.querySelectorAll('*')) {
      if (widget.contains(el)) continue;
      if (norm(el.textContent) === 'Recommandations de production' && !el.children.length) {
        el.click();
        return;
      }
    }
  }

  const debut = Date.now();
  (function attendre() {
    const r = extraire();
    const prod = r ? r.cartes.filter((c) => c.type === 'prod') : [];
    const reco = r ? r.cartes.filter((c) => c.type === 'reco') : [];
    const ecoule = Date.now() - debut;

    if (prod.length && !reco.length && ecoule > 3000) cliquerOngletReco();

    const complet = prod.length >= 4 && reco.length >= 4;
    if (!complet && ecoule < DELAI_MAX_MS && !(ecoule > 10000 && prod.length && reco.length)) {
      setTimeout(attendre, 500); // les données arrivent après le chargement de la page
      return;
    }

    contenu.innerHTML = '';
    if (!r || (!prod.length && !reco.length)) {
      statut.textContent = 'Introuvable : connecte-toi (bouton Site)';
      widget.querySelector('#sn-diag').style.display = 'block';
      return;
    }
    section('Production en cours', prod);
    section('Recommandations de production', reco);
    statut.textContent = 'Mis à jour à ' + heure(new Date());
    if (!prod.length || !reco.length) widget.querySelector('#sn-diag').style.display = 'block';
  })();
})();
