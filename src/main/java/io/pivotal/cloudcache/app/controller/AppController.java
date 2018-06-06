/*
 * Copyright (C) 2018-Present Pivotal Software, Inc. All rights reserved.
 * This program and the accompanying materials are made available under
 * the terms of the under the Apache License, Version 2.0 (the "License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.pivotal.cloudcache.app.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.apache.geode.LogWriter;
import org.apache.geode.cache.GemFireCache;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.pivotal.cloudcache.app.model.Pizza;
import io.pivotal.cloudcache.app.repository.NameRepository;
import io.pivotal.cloudcache.app.repository.PizzaRepository;

@RestController
@SuppressWarnings("unused")
public class AppController {

    private GemFireCache gemfireCache;

    private NameRepository nameRepository;

    private PizzaRepository pizzaRepository;

    public AppController(GemFireCache gemfireCache, NameRepository nameRepository, PizzaRepository pizzaRepository) {

        this.gemfireCache = gemfireCache;
        this.nameRepository = nameRepository;
        this.pizzaRepository = pizzaRepository;
    }

    @GetMapping("/nukeAndPave")
    public String nukeAndPave() {

        this.nameRepository.deleteAll();
        this.pizzaRepository.deleteAll();

        return "<h1>OVEN EMPTY!</h1>";
    }

    @GetMapping("/ping")
    public String ping() {
        return "<h1>PONG!</h1>";
    }

    @RequestMapping("/preheatOven")
    public ResponseEntity<Object> preheatOven() {

        LogWriter logger = gemfireCache.getLogger();

        Pizza plainPizza = makePlainPizza();
        Pizza fancyPizza = makeFancyPizza();
        Pizza superFancyPizza = makeSuperFancyPizza("test");

        this.pizzaRepository.save(plainPizza);
        this.pizzaRepository.save(fancyPizza);
        this.pizzaRepository.save(superFancyPizza);

        logger.info("Finished baking pizzas");

        Optional<Pizza> pizza = this.pizzaRepository.findById("plain");

        if (!pizza.isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if (!pizza.filter(it -> it.uses(Pizza.Sauce.TOMATO)).isPresent()) {

            logger.info(String.format("I ordered tomato sauce; Pizza was [%s]",
                pizza.map(Pizza::toString).orElse(null)));

            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (!pizza.filter(it -> it.has(Pizza.Topping.CHEESE)).isPresent()) {

            logger.info(String.format("Where's my cheese? Pizza was [%s]",
                pizza.map(Pizza::toString).orElse(null)));

            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>("<h1>OVEN HEATED!</h1>", HttpStatus.OK);
    }

    @GetMapping("/pizzas")
    public Object getPizzas() {

        Iterable<Pizza> pizzas = this.pizzaRepository.findAll();

        return nullSafeIterable(pizzas).iterator().hasNext() ? pizzas : "<h1>No Pizzas Found</h1>";
    }

    @GetMapping("/pizzas/{name}")
    public Object getNamedPizza(@PathVariable("name") String name) {

        Pizza namedPizza = this.pizzaRepository.findById(name).orElse(null);

        return namedPizza != null ? namedPizza : String.format("<h1>Pizza [%s] Not Found</h1>", name);
    }

    @GetMapping("/pizzas/order/{name}")
    public String order(@PathVariable("name") String name,
            @RequestParam(name = "sauce", defaultValue = "TOMATO") Pizza.Sauce pizzaSauce,
            @RequestParam(name = "toppings", defaultValue = "CHEESE") Pizza.Topping[] toppings) {

        Pizza namedPizza = Pizza.named(name).having(pizzaSauce);

        Arrays.stream(toppings).forEach(namedPizza::with);

        this.pizzaRepository.save(namedPizza);

        return String.format("<h1>Pizza [%s] Ordered</h1>", namedPizza);
    }


    // Technically, this should be a POST, but...
    @GetMapping("/pizzas/pestoOrder/{name}")
    public String pestoOrder(@PathVariable("name") String name) {

        this.pizzaRepository.save(makeSuperFancyPizza(name));

        return String.format("<h1>Pesto Pizza [%s] Ordered</h1>", name);
    }

    private Pizza makeFancyPizza() {

        return Pizza.named("fancy")
            .having(Pizza.Sauce.ALFREDO)
            .with(Pizza.Topping.ARUGULA)
            .with(Pizza.Topping.CHICKEN);
    }

    private Pizza makePlainPizza() {
        return Pizza.named("plain").with(Pizza.Topping.CHEESE);
    }

    private Pizza makeSuperFancyPizza(String name) {

        return Pizza.named(name)
            .having(Pizza.Sauce.PESTO)
            .with(Pizza.Topping.CHICKEN)
            .with(Pizza.Topping.PARMESAN)
            .with(Pizza.Topping.CHERRY_TOMATOES);
    }

    private <T> Iterable<T> nullSafeIterable(Iterable<T> iterable) {
        return iterable != null ? iterable : Collections::emptyIterator;
    }
}
