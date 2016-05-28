(ns keechma-forms-example.core
  (:require [reagent.core :as reagent]
            [forms.core :as f]
            [forms.validator :as v]))

(def validations
  "Shared validator definitions"
  {:not-empty {:message "can't be empty"
               :validator (fn [v] (not (empty? v)))}
   :valid-email {:message "is not valid email"
                 :validator (fn [v] (not= -1 (.indexOf (or v "") "@")))}
   :long-enough {:message "is too short"
                 :validator (fn [v] (> (count v) 6))}})

(defn to-validator
  "Helper function that extracts the validator definitions."
  [validations config]
  (reduce-kv (fn [m attr v]
               (assoc m attr
                      (map (fn [k] [k (get-in validations [k :validator])]) v))) {} config))

;; The form works on the following data structure:

;; {:username "retro"
;;  :password "keechmaI$4w3$0m3"
;;  :name "Mihael Konjevic"
;;  :email "konjevic@gmail.com"
;;  :accounts [{:network "twitter"
;;              :username "mihaelkonjevic"}]
;;  :phone-numbers ["000/000-0000"]}

(def validator
  "Defines the validator for the form."
  (v/validator
   (to-validator validations
                 {:username [:not-empty]
                  :name [:not-empty]
                  :email [:valid-email]
                  :password [:long-enough]
                  :accounts.*.username [:not-empty] ;; Validate username for each account
                  :accounts.*.network [:not-empty] ;; Validate network for each account
                  :accounts [:not-empty] ;; Account list must have entries
                  :phone-numbers.* [:not-empty]}))) ;; Validate each phone number in a list

(def form
  "Bind validator to the form"
  (f/constructor validator))

(def inited-form
  "Create a form instance"
  (form {}))

(defn setter
  "Set the value of the key path in the data atom"
  [path data-atom]
  (fn [e]
    (swap! data-atom assoc-in path (.. e -target -value))))

(defn add-social-media-account
  "Adds a new entry to the `:accounts` attribute. After the data is added it will
  validate the form to potentially remove the validation error for the `:accounts`
  attribute."
  [form]
  (fn [e]
    (.preventDefault e)
    (let [form-data-atom (f/data form)
          form-data @form-data-atom
          accounts (or (:accounts form-data) [])]
      (swap! form-data-atom assoc :accounts (conj accounts {:username nil :network nil}))
      (f/validate! form true))))

(defn add-phone-number
  "Adds a new entry to the `:phone-numbers` attribute. After the data is added it will
  validate the form to potentially remove the validation error for the `:phone-numbers`
  attribute."
  [form]
  (fn [e]
    (.preventDefault e)
    (let [form-data-atom (f/data form)
          form-data @form-data-atom
          accounts (or (:phone-numbers form-data) [])]
      (swap! form-data-atom assoc :phone-numbers (conj accounts nil))
      (f/validate! form true))))

(defn render-errors
  "Renders the errors for the given key-path."
  [form path]
  (let [errors @(f/errors-for-path form path)]
    (when errors
      [:div.text-danger.errors-wrap 
       [:ul.list-unstyled
        (map (fn [error]
               [:li {:key error} (get-in validations [error :message])]) (:failed errors))]])))

(defn render-input
  "Renders an input field and it's errors.

  Validation behaves differently based on the key path error state:

  - If the key path is in the `valid` state, validation will be triggered on blur
  - If the key path is in the `invalid` state, validation will be triggered on change
  "
  ([form path label] (render-input form path label :text))
  ([form path label type]
   (fn [] 
     (let [form-data-atom (f/data form)
           form-data @form-data-atom
           is-valid? @(f/is-valid-path? form path)
           input-setter (setter path form-data-atom)
           on-change-handler (fn [e]
                               (input-setter e)
                               (when (not is-valid?)
                                 (f/validate! form true)))
           errors @(f/errors-for-path form path)]
       [:div.form-group {:class (when (not is-valid?) "has-error")}
        [:label.control-label label]
        [:input.form-control {:type type
                              :value (get-in form-data path)
                              :on-change on-change-handler
                              :on-blur #(f/validate! form true)}]
        [render-errors form path]]))))

(defn render-social-media-select
  "Renders the select box for the social network. After the social network is selected
  it will validate the form to show or remove the error messages."
  [form path]
  (fn []
    (let [form-data-atom (f/data form)
          form-data @form-data-atom
          is-valid? @(f/is-valid-path? form path)
          select-setter (setter path form-data-atom)
          set-and-validate (fn [e]
                             (select-setter e)
                             (f/validate! form true))]
      [:div.form-group {:class (when (not is-valid?) "has-error")}
       [:label.control-label "Social Network"]
       [:select.form-control {:on-change set-and-validate 
                              :value (get-in form-data path)}
        [:option {:value ""} "Please select"]
        [:option {:value "facebook"} "Facebook"]
        [:option {:value "twitter"} "Twitter"]
        [:option {:value "instagram"} "Instagram"]]
       [render-errors form path]])))

(defn render-social-media-accounts
  "Renders a list of the social network accounts (nested) form fields."
  [form]
  (fn []
    (let [form-data-atom (f/data form)
          form-data @form-data-atom
          accounts (:accounts form-data)]
      (when (pos? (count accounts))
        [:div.well.nested-wrap 
         (doall (map-indexed
                 (fn [idx item]
                   [:div.social-network-account-wrap {:key idx}
                    [:h3 (str "Account #" (inc idx))]
                    [render-social-media-select form [:accounts idx :network]]
                    [render-input form [:accounts idx :username] "Username"]]) accounts))]))))

(defn render-phone-numbers
  "Renders a list of the phone number input fields"
  [form]
  (fn []
    (let [form-data-atom (f/data form)
          form-data @form-data-atom
          phone-numbers (:phone-numbers form-data)]
      [:div.nested-wrap 
       (doall (map-indexed
               (fn [idx item]
                 [:div {:key idx}
                  [render-input form [:phone-numbers idx] (str "Phone number #" (inc idx))]]) phone-numbers))])))

(defn forms-render
  "Main template, renders the form and all of the fields. When the form is submitted
  it will validate the whole form which will potentially render the errors."
  [form]
  (let [on-submit (fn [e]
                    (.preventDefault e)
                    (f/validate! form))]
    [:div.container>div.row>div.col-xs-12
     [:form {:on-submit on-submit}
      [:h1 "User Info"]
      [render-input form [:username] "Username"]
      [render-input form [:password] "Password" :password]
      [render-input form [:name] "Name"]
      [render-input form [:email] "Email"]
      [:hr]
      [:h2 "Social Network accounts"]
      [:button.btn
       {:on-click (add-social-media-account form)}
       "Add Social Network account"]
      [render-social-media-accounts form]
      [render-errors form :accounts]
      [:hr]
      [:h2 "Phone Numbers"]
      [:button.btn
       {:on-click (add-phone-number form)}
       "Add Phone Number"]
      [render-phone-numbers form]
      [:hr]
      [:button.btn.btn-primary "Submit"]]]))

(defn reload []
  (reagent/render [forms-render inited-form]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (reload))
