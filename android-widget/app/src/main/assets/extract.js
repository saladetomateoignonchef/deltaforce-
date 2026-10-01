// Injecté par l'appli dans la page du QG. Même logique que site-noir-widget.user.js (v3.1) :
// on lit les cartes de chaque onglet, on clique sur "Recommandations", puis on renvoie
// le résultat en JSON à l'appli via l'objet Java "SiteNoir".
(function () {
  if (window.__siteNoir) return;
  window.__siteNoir = true;

  var RE_TIMER = /(?<!\d)\d{1,2}:\d{2}:\d{2}(?!\d)/;
  var DELAI_MAX_MS = 25000;
  // Map : garde l'ordre d'apparition des ateliers sur le site
  var memo = { personal: new Map(), recommend: new Map() };

  function norm(s) { return (s || '').replace(/[’`´]/g, "'").replace(/\s+/g, ' ').trim(); }
  function texte(carte, sel) { var el = carte.querySelector(sel); return el ? norm(el.textContent) : ''; }
  function visible(el) { return !!el && getComputedStyle(el).display !== 'none'; }
  function valeurs(m) { return Array.from(m.values()); }
  function remplie(c) { return !!c.objet || !!c.timer; }
  function sectionRemplie(m) { return valeurs(m).some(remplie); }

  function lireCartes() {
    var cartes = document.querySelectorAll('[data-info="manufacture-card"]');
    for (var i = 0; i < cartes.length; i++) {
      var carte = cartes[i];
      var section = memo[carte.dataset.section];
      if (!section) continue;
      var atelier = texte(carte, '.typename');
      var img = carte.querySelector('[data-info="manufacture-card-image"]');
      var imgWrap = carte.querySelector('[data-info="manufacture-card-image-wrap"]');
      var pied = carte.querySelector('[data-info="manufacture-card-footer"]');
      var piedTxt = pied ? norm(pied.textContent) : '';
      var etat = '';
      if (visible(carte.querySelector('[data-info="manufacture-card-locked"]'))) etat = 'Verrouillé';
      else if (visible(carte.querySelector('[data-info="manufacture-card-upgrade"]'))) etat = 'En amélioration';
      else if (visible(carte.querySelector('[data-info="manufacture-card-empty"]'))) etat = 'Rien en cours';
      var lv = '';
      carte.classList.forEach(function (c) { if (/^lv\d$/.test(c)) lv = c; });
      var nombres = piedTxt.match(/\d[\d.,\s]*\d|\d/g);
      var info = {
        atelier: atelier,
        etat: etat,
        objet: texte(carte, '[data-info="manufacture-card-name"]'),
        image: img && (!imgWrap || visible(imgWrap)) ? (img.currentSrc || img.src || '') : '',
        lv: lv,
        timer: (piedTxt.match(RE_TIMER) || [''])[0],
        recompense: nombres ? nombres[nombres.length - 1].trim() : '',
        termine: !!pied && pied.classList.contains('collected')
      };
      // Le site affiche d'abord des cartes vides : on ne remplace jamais une carte remplie par une vide
      var cle = carte.dataset.workbenchId || atelier;
      var ancienne = section.get(cle);
      if (!ancienne || !remplie(ancienne) || remplie(info)) section.set(cle, info);
    }
  }

  function cliquerOnglet(nom) {
    var onglet = document.querySelector('[data-action="m-manufacture-tab"][data-tab="' + nom + '"]');
    if (onglet) onglet.click();
  }

  var debut = Date.now();
  var dernierClic = 0;
  (function attendre() {
    lireCartes();
    var prodOk = sectionRemplie(memo.personal);
    var recoOk = sectionRemplie(memo.recommend);
    var ecoule = Date.now() - debut;

    if ((prodOk || ecoule > 10000) && !recoOk && Date.now() - dernierClic > 3000) {
      dernierClic = Date.now();
      cliquerOnglet('recommend');
    }
    if (!(prodOk && recoOk) && ecoule < DELAI_MAX_MS) {
      setTimeout(attendre, 400);
      return;
    }
    if (!prodOk && !recoOk) {
      SiteNoir.erreur('Pas de données : ouvre l’appli pour te connecter');
      return;
    }
    SiteNoir.resultat(JSON.stringify({ personal: valeurs(memo.personal), recommend: valeurs(memo.recommend) }));
  })();
})();
