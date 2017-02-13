# sbt-hepek

Welcome to sbt-hepek, an [sbt](http://www.scala-sbt.org) plugin for rendering Scala `object`s to files.

## Installing

Sbt-hepek is just like any other plugin for sbt, the installation is done by adding following lines to the `project/plugins.sbt` file:

```scala
resolvers += Resolver.sonatypeRepo("snapshots")
addSbtPlugin("ba.sake" % "sbt-hepek" % "0.0.1-SNAPSHOT")
```

Note that this is the first version of this project, currently in development (SNAPSHOT).

## Using

There is one main goal of sbt-hepek, called `hepek`. 
When executed, it will render all Scala `object`s that extend [`Renderable`](https://github.com/sake92/hepek-core/blob/master/src/main/java/ba/sake/hepek/core/Renderable.java) 
trait to respective files, relative to the `hepekTarget` folder.  

Default value for `hepekTarget` is `hepekTarget := target.value / "web" / "public" / "main"`.  
The good old `target` folder.

That's all there is to it, for now...  
For a more comprehensive example see the [hepek-examples](https://github.com/sake92/hepek-examples) repo.

## Contact

Author of the plugin is Sakib Hadžiavdić.  
Twitter: @[sake_92](https://twitter.com/sake_92)  
Email: sakib@sake.ba

## License
This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
