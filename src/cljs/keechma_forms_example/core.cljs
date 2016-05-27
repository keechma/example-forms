(ns keechma-forms-example.core
  (:require [reagent.core :as reagent]
            [forms.core :as f]
            [forms.validator :as v]))

(def validations {:not-empty {:message "can't be empty"
                              :validator (fn [v] (not (empty? v)))}
                  :valid-email {:message "is not valid email"
                                :validator (fn [v] (not= -1 (.indexOf (or v "") "@")))}
                  :long-enough {:message "is too short"
                                :validator (fn [v] (> (count v) 6))}})

(defn to-validator [validations config]
  (reduce-kv (fn [m attr v]
               (assoc m attr
                      (map (fn [k] [k (get-in validations [k :validator])]) v))) {} config))

(def validator (v/validator
                (to-validator validations
                              {:username [:not-empty]
                               :name [:not-empty]
                               :email [:valid-email]
                               :password [:long-enough]
                               :accounts.*.username [:not-empty] ;; Validate username for each account
                               :accounts.*.network [:not-empty] ;; Validate network for each account
                               :accounts [:not-empty] ;; Account list must have entries
                               :phone-numbers.* [:not-empty]}))) ;; Validate each phone number in a list

;; The form works on the following structure:

;; {:username "retro"
;;  :password "keechmaI$4w3$0m3"
;;  :name "Mihael Konjevic"
;;  :email "konjevic@gmail.com"
;;  :accounts [{:network "twitter"
;;              :username "mihaelkonjevic"}]
;;  :phone-numbers ["000/000-0000"]}


(def form (f/constructor validator))
(def inited-form (form {} {:auto-validate? true}))

(defn setter [path data-atom]
  (fn [e]
    (swap! data-atom assoc-in path (.. e -target -value))))

(defn add-social-media-account [inited-form]
  (fn [e]
    (.preventDefault e)
    (let [form-data-atom (f/data inited-form)
          form-data @form-data-atom
          accounts (or (:accounts form-data) [])]
      (swap! form-data-atom assoc :accounts (conj accounts {:username nil :network nil})))))

(defn add-phone-number [inited-form]
  (fn [e]
    (.preventDefault e)
    (let [form-data-atom (f/data inited-form)
          form-data @form-data-atom
          accounts (or (:phone-numbers form-data) [])]
      (swap! form-data-atom assoc :phone-numbers (conj accounts nil)))))

(defn render-errors [form path]
  (let [errors @(f/errors-for-path form path)]
    (when errors
      [:div
       [:b "Errors:"]
       [:ul.text-danger
        (map (fn [error]
               [:li {:key error} (get-in validations [error :message])]) (:failed errors))]])))

(defn render-input
  ([form path label] (render-input form path label :text))
  ([form path label type]
   (fn [] 
     (let [form-data-atom (f/data form)
           form-data @form-data-atom
           errors @(f/errors-for-path form path)]
       [:div.form-group {:class (when errors "has-errors")}
        [:label label]
        [:input.form-control {:type type
                              :value (get-in form-data path)
                              :on-change (setter path form-data-atom)}]
        [render-errors form path]]))))

(defn render-social-media-select [form path]
  (fn []
    (let [form-data-atom (f/data form)
          form-data @form-data-atom
          errors @(f/errors-for-path form path)]
      [:div.form-group {:class (when errors "has-errors")}
       [:label "Social Network"]
       [:select.form-control {:on-change (setter path form-data-atom)
                              :value (get-in form-data path)}
        [:option {:value ""} "Please select"]
        [:option {:value "facebook"} "Facebook"]
        [:option {:value "twitter"} "Twitter"]
        [:option {:value "instagram"} "Instagram"]]
       [render-errors form path]])))

(defn render-social-media-accounts [form]
  (fn []
    (let [form-data-atom (f/data form)
          form-data @form-data-atom
          accounts (:accounts form-data)]
      [:div {:style {:margin-top "20px"}}
       (doall (map-indexed
               (fn [idx item]
                 [:div {:key idx}
                  [render-social-media-select form [:accounts idx :network]]
                  [render-input form [:accounts idx :username] (str "Account #" (inc idx))]]) accounts))])))

(defn render-phone-numbers [form]
  (fn []
    (let [form-data-atom (f/data form)
          form-data @form-data-atom
          phone-numbers (:phone-numbers form-data)]
      [:div {:style {:margin-top "20px"}}
       (doall (map-indexed
               (fn [idx item]
                 [:div {:key idx}
                  [render-input form [:phone-numbers idx] (str "Phone number #" (inc idx))]]) phone-numbers))])))

(defn forms-render [inited-form]
  (let [on-submit (fn [e]
                    (.preventDefault e)
                    (f/validate! inited-form))]
    [:div.container>div.row>div.col-xs-12
     [:form {:on-submit on-submit}
      [:h1 "User Info"]
      [render-input inited-form [:username] "Username"]
      [render-input inited-form [:password] "Password" :password]
      [render-input inited-form [:name] "Name"]
      [render-input inited-form [:email] "Email"]
      [:hr]
      [:h2 "Social Network accounts"]
      [:button.btn
       {:on-click (add-social-media-account inited-form)}
       "Add Social Network account"]
      [render-social-media-accounts inited-form]
      [render-errors inited-form :accounts]
      [:hr]
      [:h2 "Phone Numbers"]
      [:button.btn
       {:on-click (add-phone-number inited-form)}
       "Add Phone Number"]
      [render-phone-numbers inited-form]
      [:hr]
      [:button.btn.btn-primary "Submit"]]]))

(defn reload []
  (reagent/render [forms-render inited-form]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (reload))
